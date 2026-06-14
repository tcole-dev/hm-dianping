package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private StringRedisTemplate stringRedisTemplate;
    private CacheUtil cacheUtil;

    // 延迟双删的异步调度线程池（单线程即可，仅用于定时删缓存）
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate, CacheUtil cacheUtil) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheUtil = cacheUtil;
    }

    // 重写查询方法，增加缓存逻辑
    @Override
    public Result queryById(Long id) {
        /*
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 缓存查询
        String value = stringRedisTemplate.opsForValue().get(key);
         */
        Shop shop = cacheUtil.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY, id, this::getById, Shop.class, 30L, TimeUnit.MINUTES);
        if (shop != null) {
            // 存在，直接返回
            return Result.ok(shop);
        }
        // 不存在，根据id查询数据库
        shop = getById(id);
        if (shop == null) {
            // 店铺不存在，返回错误
            return Result.fail("店铺不存在");
        }
        // 存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
        cacheUtil.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY +  id, shop, 30L, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    // 重写修改方法，增加缓存逻辑/数据库一致性
    @Override
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        // 先修改数据库
        updateById(shop);
        // 后删除缓存（数据库、缓存一致性）
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(cacheKey);
        // 异步延迟双删：500ms 后再删一次，避免阻塞请求线程
        SCHEDULER.schedule(() -> stringRedisTemplate.delete(cacheKey), 500, TimeUnit.MILLISECONDS);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 计算分页参数
        // 跳过多少页
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        // 本次分页显示多少条数据
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        // 空集，无数据
        if (results == null) {
            return Result.ok();
        }
        // 获取具体数据List
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();

        // 没有下一页
        if (content.size() <= from) {
            return Result.ok();
        }
        // 获取当前页数据
        var ids = new ArrayList<Long>();
        var distanceMap = new HashMap<String, Distance>();

        content.stream().skip(from).forEach(result -> {
            // 获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 查询Shop数据（不拼接 SQL，避免注入风险）
        List<Shop> shops = query().in("id", ids).list();
        // Java 层按 Redis GEO 距离排序的顺序排列
        var orderMap = new HashMap<Long, Integer>();
        for (int i = 0; i < ids.size(); i++) {
            orderMap.put(ids.get(i), i);
        }
        shops.sort((a, b) -> {
            int ia = orderMap.getOrDefault(a.getId(), Integer.MAX_VALUE);
            int ib = orderMap.getOrDefault(b.getId(), Integer.MAX_VALUE);
            return Integer.compare(ia, ib);
        });
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
