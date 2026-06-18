package com.hmdp.consumer;

import lombok.Data;

/**
 * 秒杀订单 MQ 消息体
 */
@Data
public class SeckillOrderMessage {
    private Long userId;
    private Long voucherId;
}
