package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock {
    private static final String KEY_PREFIX = "lock:";
    private final StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
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
        String value = "Thread:" + Thread.currentThread().getId();
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, TimeUnit.SECONDS));
    }

    public void unLock() {
//        String key = KEY_PREFIX + UserHolder.getUser().getId().toString();
//        String value = stringRedisTemplate.opsForValue().get(key);
//        if (Objects.equals(value, "Thread:" + Thread.currentThread().getId())) {
//            stringRedisTemplate.delete(key);
//            return true;
//        }
//        return false;
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(
                        KEY_PREFIX + UserHolder.getUser().getId().toString()),
                "Thread:" + Thread.currentThread().getId());
    }
}
