package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一id生成器
 * 使用Redis自增
 */
@Component
public class GlobalUniqueIdUtil {
    private StringRedisTemplate stringRedisTemplate;
    public GlobalUniqueIdUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // begin时间戳
    private final long START_TIME = 1640995200L;
    // 序列号位数
    private final long COUNT_BITS = 32;
    private final String KEY_PREFIX = "unique:";

    public long nextId(String prefix) {
        // 生成时间戳
        LocalDateTime localDateTime = LocalDateTime.now();
        long now = localDateTime.toEpochSecond(ZoneOffset.UTC);
        long time = now - START_TIME;
        // 当前日期
        String date = localDateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 获取自增量
        long count = stringRedisTemplate.opsForValue().increment(KEY_PREFIX + prefix + ":" + date);

        return time << COUNT_BITS | count;
    }
}
