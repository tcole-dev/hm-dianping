package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.config.CacheProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 缓存工具类
 * 解决缓存穿透、击穿、雪崩
 * 缓存空对象、逻辑过期、随机TTL
 */
@Component
public class CacheUtil {
    private final StringRedisTemplate stringRedisTemplate;
    private ThreadPoolExecutor executor;

    /** L1 本地缓存（Caffeine） */
    private final Cache<String, Object> localCache;
    /** 空值哨兵，避免 Caffeine 不允许 null 值 */
    private static final Object NULL_SENTINEL = new Object();

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("cache_unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public CacheUtil(StringRedisTemplate stringRedisTemplate, CacheProperties cacheProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(cacheProperties.getMaxSize())
                .expireAfterWrite(cacheProperties.getTtl(), TimeUnit.SECONDS)
                .build();
        // 用于击穿时的重建缓存
        executor = new ThreadPoolExecutor(
                5,                      // corePoolSize
                12,                     // maximumPoolSize
                60L,                    // keepAliveTime
                TimeUnit.SECONDS,       // unit
                new ArrayBlockingQueue<>(100),  // workQueue
                Executors.defaultThreadFactory(), // threadFactory
                new ThreadPoolExecutor.CallerRunsPolicy() // handler
        );
        executor.allowCoreThreadTimeOut(true);
    }

    /**
     * 缓存数据
     * @param key 缓存的key
     * @param value 缓存的value
     * @param time 缓存时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 带有逻辑过期的缓存（同步更新 L1）
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        long seconds = unit.toSeconds(time);    // 逻辑过期时间
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(seconds), value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), seconds * 2, TimeUnit.SECONDS);
        localCache.put(key, value);
    }

    /** 驱逐 L1 缓存 */
    public void evictL1(String key) {
        localCache.invalidate(key);
    }

    // 带有随机TTL的缓存
    public void setWithRandomTTL(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time + RandomUtil.randomLong(0, time), unit);
    }

    // 带有随机TTL的列表集体缓存
    public void setListWithRandomTTL(String redisPrefix,Map<String, Object> map, Long time, TimeUnit unit) {
        map.forEach((key, value) -> {
            String newKey = redisPrefix + key;
            stringRedisTemplate.opsForValue().set(newKey, JSONUtil.toJsonStr(value), time + RandomUtil.randomLong(0, time), unit);
        });
    }

    /**
     * 缓存空对象查询(解决穿透)
     * @param redisPrefix 对不同模块使用的不同的缓存前缀
     * @param id 查询的id
     * @param dbQuery 数据库查询的lambda
     * @param time 缓存时间
     * @param unit 时间单位
     * @return 数据库、Redis缓存json对应的对象
     * @param <ID> 查询的id的类型 String/Integer
     * @param <T> 查询的返回值类型
     */
    public <ID, T> T queryWithNullCache(String redisPrefix, ID id, Function<ID, T> dbQuery,Class<T> clazz, Long time, TimeUnit unit) {
        String key = redisPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            if (json.equals("NULL")) {
                return null;
            }
            return JSONUtil.toBean(json, clazz);
        }
        T t = dbQuery.apply(id);
        if (t == null) {
            stringRedisTemplate.opsForValue().set(key, "NULL", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(t), time, unit);
        return t;
    }

    /**
     * 带有逻辑过期的缓存查询(解决击穿)
     * @param redisPrefix 对不同模块使用的不同的缓存前缀
     * @param id 缓存的id
     * @param dbQuery 数据库查询的lambda
     * @param clazz 缓存的返回值类型，与T对应
     * @param time 缓存时间
     * @param unit 时间单位
     * @return 数据库、Redis缓存json对应的对象
     * @param <ID> 缓存的id的类型 String/Integer
     * @param <T> 缓存的返回值类型
     */
    public <ID, T> T queryWithLogicExpire(String redisPrefix, ID id, Function<ID, T> dbQuery,Class<T> clazz, Long time, TimeUnit unit) {
        String key = redisPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        String value = JSONUtil.toJsonStr(redisData.getData());
        // 缓存未过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return JSONUtil.toBean(value, clazz);
        }
        // 缓存已过期，缓存重建
        String redoKey = redisPrefix + ":redoLock:" + id;
        // 前置检查：如果锁已存在，说明其他实例/线程正在重建，跳过提交避免无谓争抢
        String existingLock = stringRedisTemplate.opsForValue().get(redoKey);
        if (existingLock != null) {
            // 已有线程在重建，直接返回旧数据
            return JSONUtil.toBean(value, clazz);
        }
        String lockValue = UUID.randomUUID().toString();
        executor.submit(() -> reBuildCacheTask(redoKey, lockValue, key, id, dbQuery, time, unit));
        return JSONUtil.toBean(value, clazz);
    }

    /**
     * 二级缓存查询（L1 Caffeine + L2 Redis 逻辑过期）
     * 命中 L1 直接返回 → 查 L2 并回填 L1 → 都未命中查 DB 并回填 L2+L1
     */
    @SuppressWarnings("unchecked")
    public <ID, T> T queryWithTwoLevel(String redisPrefix, ID id, Function<ID, T> dbQuery, Class<T> clazz, Long time, TimeUnit unit) {
        String key = redisPrefix + id;
        // 1. 查 L1
        Object l1Value = localCache.getIfPresent(key);
        if (l1Value == NULL_SENTINEL) {
            return null;
        }
        if (l1Value != null) {
            return (T) l1Value;
        }
        // 2. 查 L2（Redis 逻辑过期）
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.isEmpty()) {
            RedisData redisData = JSONUtil.toBean(json, RedisData.class);
            String value = JSONUtil.toJsonStr(redisData.getData());
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                T result = JSONUtil.toBean(value, clazz);
                localCache.put(key, result);
                return result;
            }
            // L2 逻辑过期，异步重建，返回旧数据
            String redoKey = redisPrefix + ":redoLock:" + id;
            String existingLock = stringRedisTemplate.opsForValue().get(redoKey);
            if (existingLock == null) {
                String lockValue = UUID.randomUUID().toString();
                executor.submit(() -> reBuildCacheTask(redoKey, lockValue, key, id, dbQuery, time, unit));
            }
            T result = JSONUtil.toBean(value, clazz);
            localCache.put(key, result);
            return result;
        }
        // 3. L1+L2 都未命中，查 DB
        T result = dbQuery.apply(id);
        if (result == null) {
            localCache.put(key, NULL_SENTINEL);
            return null;
        }
        // 回填 L2 + L1
        setWithLogicExpire(key, result, time, unit);
        return result;
    }

    /**
     * 提交给线程池进行的缓存重建任务
     * @param redoKey 缓存重建的分布式锁
     * @param lockValue 缓存锁的value（校验owner）
     * @param key 缓存的key
     * @param id 用于查询的id
     * @param dbQuery 数据库查询的lambda
     * @param time 缓存时间
     * @param unit 时间单位
     * @param <ID> 查询的id的类型 String/Integer
     * @param <T> 缓存的返回值类型
     */
    private <ID,T> void reBuildCacheTask(String redoKey, String lockValue,String key, ID id, Function<ID, T> dbQuery, Long time, TimeUnit unit) {
        // 获取锁失败，则返回旧数据
        if (!tryLock(redoKey, lockValue)) {
            return;
        }
        T t = dbQuery.apply(id);
        this.setWithLogicExpire(key, t, time, unit);
        unlock(redoKey, lockValue);
    }

    /**
     * 带有随机TTL的缓存查询(解决雪崩)
     * @param redisPrefix 对不同模块使用的不同的缓存前缀
     * @param id 缓存的id
     * @param dbQuery 数据库查询的lambda
     * @param clazz 缓存的返回值类型，与T对应
     * @param time 缓存时间
     * @param unit 时间单位
     * @return 数据库、Redis缓存json对应的对象
     * @param <ID> 缓存的id的类型 String/Integer
     * @param <T> 缓存的返回值类型
     */
    public <ID, T> T queryWithRandomTTL(String redisPrefix, ID id, Function<ID, T> dbQuery, Class<T> clazz, Long time, TimeUnit unit) {
        String key = redisPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.equals("NULL")) {
            return JSONUtil.toBean(json, clazz);
        }
        T t = dbQuery.apply(id);
        if (t != null) {
            this.setWithRandomTTL(key, t, time, unit);
        }
        return t;
    }

    private boolean tryLock(String key, String  value) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, 200, TimeUnit.SECONDS));
    }

    /**
     * 使用 Lua 脚本原子解锁，避免 GET + DELETE 之间的竞态条件
     */
    private void unlock(String key, String value) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
    }
}
