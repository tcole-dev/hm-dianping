package com.hmdp.controller;

import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    // 限流：每用户 60 秒内最多 5 次秒杀请求
    @RateLimit(key = "rate:seckill", count = 5, time = 60)
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return Result.ok(voucherOrderService.seckillVoucher(voucherId));
    }
}
