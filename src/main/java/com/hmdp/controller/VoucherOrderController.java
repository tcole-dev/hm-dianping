package com.hmdp.controller;

import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 限流：每用户 60 秒内最多 5 次秒杀请求
    @RateLimit(key = "rate:seckill", count = 5, time = 60)
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        // 返回工单 ID，前端用于轮询结果
        return Result.ok(voucherOrderService.seckillVoucher(voucherId));
    }

    /**
     * 轮询秒杀结果
     * PENDING → 继续轮询
     * SUCCESS → 返回 orderId，前端跳转订单详情
     * FAILED → 返回失败原因
     */
    @GetMapping("status/{ticketId}")
    public Result getSeckillStatus(@PathVariable("ticketId") String ticketId) {
        String key = RedisConstants.SECKILL_TICKET_KEY + ticketId;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return Result.fail("工单不存在或已过期");
        }
        return Result.ok(entries);
    }
}
