package com.hmdp.service.impl;

import com.hmdp.consumer.SeckillOrderMessage;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀下单服务
 * 同步：校验 + 一人一单 + 扣库存（@Transactional）
 * 事务提交后：写 PENDING 工单 + 发 MQ 事务消息
 * 异步：SeckillOrderConsumer 创建订单 → 更新工单为 SUCCESS
 * 兜底：SeckillCompensationTask 扫描超时 PENDING 工单，补建单或回滚库存
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private final ISeckillVoucherService seckillVoucherService;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheUtil cacheUtil;

    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService, RedissonClient redissonClient, RocketMQTemplate rocketMQTemplate, StringRedisTemplate stringRedisTemplate, CacheUtil cacheUtil) {
        this.seckillVoucherService = seckillVoucherService;
        this.redissonClient = redissonClient;
        this.rocketMQTemplate = rocketMQTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheUtil = cacheUtil;
    }

    @Override
    public String seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1.查询优惠券（逻辑过期缓存，防击穿）
        SeckillVoucher seckillVoucher = cacheUtil.queryWithLogicExpire(
                RedisConstants.SECKILL_VOUCHER_KEY, voucherId,
                seckillVoucherService::getById, SeckillVoucher.class,
                30L, java.util.concurrent.TimeUnit.MINUTES);
        if (seckillVoucher == null) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND);
        }
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
            return createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 一人一单校验 + 扣库存 + 事务提交后发 MQ 消息
     * @return 工单 ID（userId:voucherId），前端轮询用
     */
    @Transactional
    public String createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 一人一单校验
        Long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            throw new BusinessException(ErrorCode.VOUCHER_ALREADY_PURCHASED);
        }

        // 扣减库存（事务内，失败自动回滚）
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!result) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }

        // 工单 ID
        String ticketId = userId + ":" + voucherId;

        // 事务提交后执行：写 PENDING 工单 + 发 MQ 事务消息
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // 写 PENDING 工单到 Redis
                    Map<String, String> ticket = new HashMap<>(4);
                    ticket.put("status", "PENDING");
                    ticket.put("voucherId", voucherId.toString());
                    ticket.put("userId", userId.toString());
                    stringRedisTemplate.opsForHash().putAll(
                            RedisConstants.SECKILL_TICKET_KEY + ticketId, ticket);
                    stringRedisTemplate.expire(
                            RedisConstants.SECKILL_TICKET_KEY + ticketId,
                            RedisConstants.SECKILL_TICKET_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);

                    // 发送 MQ 事务消息（扣库存已完成，listener 自动 COMMIT）
                    SeckillOrderMessage msg = new SeckillOrderMessage();
                    msg.setUserId(userId);
                    msg.setVoucherId(voucherId);
                    rocketMQTemplate.sendMessageInTransaction("seckill_order",
                            MessageBuilder.withPayload(msg)
                                    .setHeader("userId", userId)
                                    .setHeader("voucherId", voucherId)
                                    .build(),
                            null);
                    log.info("秒杀工单已创建: ticketId={}", ticketId);
                } catch (Exception e) {
                    log.error("秒杀工单创建失败（MQ发送异常）: ticketId={}", ticketId, e);
                }
            }
        });

        return ticketId;
    }
}
