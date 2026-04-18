package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 重写查询方法，增加缓存逻辑
    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 缓存查询
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value != null) {
            // 存在，直接返回
            Shop shop = JSONUtil.toBean(value, Shop.class);
            return Result.ok(shop);
        }
        // 不存在，根据id查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 店铺不存在，返回错误
            return Result.fail("店铺不存在");
        }
        // 存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
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
        return Result.ok();
    }
}
