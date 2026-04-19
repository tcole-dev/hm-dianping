package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
        cacheUtil.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY, shop, 30L, TimeUnit.MINUTES);
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
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            log.debug(e.getMessage());
        }
        // 缓存双删
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
