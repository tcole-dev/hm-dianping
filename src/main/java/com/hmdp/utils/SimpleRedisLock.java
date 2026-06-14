package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock {
    private static final String KEY_PREFIX = "lock:";
    private final StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    // 使用 UUID 作为锁标识，保证跨实例唯一性（替代 JVM 局部的 Thread.currentThread().getId()）
    private static final String LOCK_VALUE = UUID.randomUUID().toString();

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean tryLock(Long timeout) {
        String key = KEY_PREFIX + UserHolder.getUser().getId().toString();
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, LOCK_VALUE, timeout, TimeUnit.SECONDS));
    }

    public void unLock() {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(
                        KEY_PREFIX + UserHolder.getUser().getId().toString()),
                LOCK_VALUE);
    }
}
