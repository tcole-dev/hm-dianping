package com.hmdp.consumer;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.GlobalUniqueIdUtil;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 秒杀订单消费者
 * 接收 MQ 消息 → 创建订单写 DB → 更新 Redis 工单状态为 SUCCESS
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "seckill_order", consumerGroup = "seckill_order_consumer")
public class SeckillOrderConsumer implements RocketMQListener<SeckillOrderMessage> {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private GlobalUniqueIdUtil globalUniqueIdUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(SeckillOrderMessage msg) {
        Long userId = msg.getUserId();
        Long voucherId = msg.getVoucherId();
        String ticketId = userId + ":" + voucherId;
        log.info("收到秒杀订单消息: ticketId={}", ticketId);

        // 创建订单
        VoucherOrder order = new VoucherOrder();
        order.setId(globalUniqueIdUtil.nextId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        voucherOrderService.save(order);

        // 更新工单状态为 SUCCESS，写入 orderId 供前端查询
        String key = RedisConstants.SECKILL_TICKET_KEY + ticketId;
        stringRedisTemplate.opsForHash().put(key, "status", "SUCCESS");
        stringRedisTemplate.opsForHash().put(key, "orderId", order.getId().toString());
        log.info("秒杀订单创建成功: ticketId={}, orderId={}", ticketId, order.getId());
    }
}
