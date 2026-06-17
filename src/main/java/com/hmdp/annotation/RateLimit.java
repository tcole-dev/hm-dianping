package com.hmdp.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解（基于 Redis 固定窗口计数器）
 * 默认按 IP 限流，登录用户按 userId 限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /** 限流 key 前缀 */
    String key() default "rate:default";
    /** 窗口内最大请求数 */
    int count() default 10;
    /** 窗口时间（秒） */
    int time() default 60;
}
