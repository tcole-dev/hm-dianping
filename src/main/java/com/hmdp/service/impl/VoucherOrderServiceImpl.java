package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.GlobalUniqueIdUtil;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private final ISeckillVoucherService seckillVoucherService;
    private final GlobalUniqueIdUtil globalUniqueIdUtil;
//    private final StringRedisTemplate stringRedisTemplate;
    private RedissonClient redissonClient;

    public VoucherOrderServiceImpl(StringRedisTemplate stringRedisTemplate, ISeckillVoucherService seckillVoucherService, GlobalUniqueIdUtil globalUniqueIdUtil, RedissonClient redissonClient) {
        this.seckillVoucherService = seckillVoucherService;
//        this.stringRedisTemplate = stringRedisTemplate;
        this.globalUniqueIdUtil = globalUniqueIdUtil;
        this.redissonClient = redissonClient;
    }

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 修改库存可用乐观锁，但插入订单数据则需要悲观锁，这里对每个userId加锁，避免一个用户同时发起两个请求时，违反一人一卖的原则
        // intern()表示从常量池中拿到数据，userId.toString()其实是每次都new String对象，无法真正锁住（synchronized使用）

        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始/结束
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        // 加锁，为创建订单做准备。同时避免创建重复订单
        //        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate);
        RLock simpleRedisLock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = simpleRedisLock.tryLock();
        // 同时有多个线程竞争同一个用户的锁，说明一个用户同时发出了多个请求
        if (!isLock) {
            return Result.fail("请勿重复下单");
        }

        // 3.创建订单方法调用
        try {
            // 在类内部调用事务方法时会因无法绕过this而无法代理，所以需要主动获取代理对象
            VoucherOrderServiceImpl proxyVoucherOrderService = (VoucherOrderServiceImpl) AopContext.currentProxy();
            Result ans = proxyVoucherOrderService.createVoucherOrder(voucherId);
            return ans;
        } finally {
            simpleRedisLock.unlock();
        }

    }

    // 避免库存扣减，但订单创建失败等情况
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 4.扣减库存
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!result) {
            return Result.fail("库存不足");
        }
        // 5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        long orderId = globalUniqueIdUtil.nextId("order");
        voucherOrder.setId(orderId);

        save(voucherOrder);
        return Result.ok(orderId);
    }
}
