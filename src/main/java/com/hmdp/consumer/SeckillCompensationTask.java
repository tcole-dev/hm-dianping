package com.hmdp.consumer;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.GlobalUniqueIdUtil;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;

/**
 * 秒杀补偿任务
 * 扫描超时的 PENDING 工单：能建单则补建，不能则回滚库存
 */
@Slf4j
@Component
public class SeckillCompensationTask {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private GlobalUniqueIdUtil globalUniqueIdUtil;

    @Scheduled(fixedDelay = 10000)
    public void compensate() {
        // 扫描所有秒杀工单 key
        Set<String> keys = stringRedisTemplate.keys(RedisConstants.SECKILL_TICKET_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            try {
                Object status = stringRedisTemplate.opsForHash().get(key, "status");
                if (!"PENDING".equals(status)) {
                    continue;
                }

                String ticketId = key.replace(RedisConstants.SECKILL_TICKET_KEY, "");
                String[] parts = ticketId.split(":");
                Long userId = Long.parseLong(parts[0]);
                Long voucherId = Long.parseLong(parts[1]);

                // 尝试补建订单（一人一单校验）
                Long count = voucherOrderService.query()
                        .eq("user_id", userId)
                        .eq("voucher_id", voucherId)
                        .count();
                if (count > 0) {
                    // 已有订单，标记成功
                    stringRedisTemplate.opsForHash().put(key, "status", "SUCCESS");
                    log.info("补偿-订单已存在: ticketId={}", ticketId);
                    continue;
                }

                // 补建订单
                VoucherOrder order = new VoucherOrder();
                order.setId(globalUniqueIdUtil.nextId("order"));
                order.setUserId(userId);
                order.setVoucherId(voucherId);
                voucherOrderService.save(order);

                stringRedisTemplate.opsForHash().put(key, "status", "SUCCESS");
                stringRedisTemplate.opsForHash().put(key, "orderId", order.getId().toString());
                log.info("补偿-订单创建成功: ticketId={}, orderId={}", ticketId, order.getId());
            } catch (Exception e) {
                // 补建失败，回滚库存
                try {
                    String ticketId = key.replace(RedisConstants.SECKILL_TICKET_KEY, "");
                    String[] parts = ticketId.split(":");
                    Long voucherId = Long.parseLong(parts[1]);

                    seckillVoucherService.update()
                            .setSql("stock = stock + 1")
                            .eq("voucher_id", voucherId)
                            .update();

                    stringRedisTemplate.opsForHash().put(key, "status", "FAILED");
                    stringRedisTemplate.opsForHash().put(key, "reason", "订单创建失败，库存已回滚");
                    log.warn("补偿-建单失败，库存已回滚: ticketId={}", ticketId, e);
                } catch (Exception ex) {
                    log.error("补偿-库存回滚也失败: key={}", key, ex);
                }
            }
        }
    }
}
