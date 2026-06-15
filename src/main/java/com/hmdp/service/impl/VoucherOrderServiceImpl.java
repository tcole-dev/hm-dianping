package com.hmdp.service.impl;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.GlobalUniqueIdUtil;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private final ISeckillVoucherService seckillVoucherService;
    private final GlobalUniqueIdUtil globalUniqueIdUtil;
    private final RedissonClient redissonClient;

    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService, GlobalUniqueIdUtil globalUniqueIdUtil, RedissonClient redissonClient) {
        this.seckillVoucherService = seckillVoucherService;
        this.globalUniqueIdUtil = globalUniqueIdUtil;
        this.redissonClient = redissonClient;
    }

    @Override
    public Long seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始/结束
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SECKILL_NOT_START);
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SECKILL_ENDED);
        }
        if (seckillVoucher.getStock() < 1) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }

        // 加锁，一人一单
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER);
        }

        try {
            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public Long createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 一人一单校验
        Long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            throw new BusinessException(ErrorCode.VOUCHER_ALREADY_PURCHASED);
        }

        // 扣减库存
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!result) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }

        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        long orderId = globalUniqueIdUtil.nextId("order");
        voucherOrder.setId(orderId);
        save(voucherOrder);
        return orderId;
    }
}
