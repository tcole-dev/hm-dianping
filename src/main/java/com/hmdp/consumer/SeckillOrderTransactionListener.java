package com.hmdp.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;

/**
 * 秒杀订单事务消息监听器
 * 库存扣减在 Service 中完成（@Transactional），此处仅确认消息提交
 */
@Slf4j
@RocketMQTransactionListener
public class SeckillOrderTransactionListener implements RocketMQLocalTransactionListener {

    @Override
    @SuppressWarnings("rawtypes")
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        // 库存已在 Service 事务中扣减成功，直接提交消息
        Long voucherId = (Long) msg.getHeaders().get("voucherId");
        Long userId = (Long) msg.getHeaders().get("userId");
        log.info("事务消息-确认提交: voucherId={}, userId={}", voucherId, userId);
        return RocketMQLocalTransactionState.COMMIT;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 正常流程不会走到回查，直接提交
        return RocketMQLocalTransactionState.COMMIT;
    }
}
