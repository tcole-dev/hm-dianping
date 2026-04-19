package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
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

    public CacheUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
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
     * 带有逻辑过期的缓存
     * @param key 缓存的key
     * @param value 缓存的value
     * @param time 缓存时间（用于逻辑过期，redis的key本身不过期）
     * @param unit 时间单位
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        long seconds = unit.toSeconds(time);
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(seconds), value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), seconds * 2, TimeUnit.SECONDS);
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
        String lockValue = UUID.randomUUID().toString();
        /**
        // 获取锁失败，则返回旧数据
        if (!tryLock(redoKey, lockValue)) {
            String value = JSONUtil.toJsonStr(redisData.getData());
            return JSONUtil.toBean(value, clazz);
        }
        T t = dbQuery.apply(id);
        this.setWithLogicExpire(key, t, time, unit);
        unlock(redoKey, lockValue);
        return t;
         */
        executor.submit(() -> reBuildCacheTask(redoKey, lockValue, key, id, dbQuery, time, unit));
        return JSONUtil.toBean(value, clazz);
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
    private void unlock(String key, String value) {
        if (Objects.equals(stringRedisTemplate.opsForValue().get(key), value)) {
            stringRedisTemplate.delete(key);
        }
    }
}
