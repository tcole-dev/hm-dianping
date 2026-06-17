package com.hmdp.aspect;

import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.UserDTO;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

/**
 * 限流切面：拦截 @RateLimit 注解的方法
 * 限流 key = 注解 key + userId（已登录）或 IP（未登录）
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private HttpServletRequest request;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("rate_limit.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    /**
     * 环绕通知：执行 Lua 脚本原子计数，超限直接拒绝
     * Lua 返回 1=放行，0=拒绝
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        // key = 注解前缀 + 用户维度（userId 或 IP）
        String key = rateLimit.key() + ":" + resolveKey();
        // 执行 Lua：INCR + EXPIRE + 判断，单次 Redis 调用保证原子性
        Long result = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(rateLimit.count()),
                String.valueOf(rateLimit.time())
        );
        if (result == null || result == 0L) {
            log.warn("限流触发: key={}", key);
            throw new BusinessException(ErrorCode.RATE_LIMIT);
        }
        return pjp.proceed();
    }

    /** 优先用 userId 限流，未登录降级为 IP 限流 */
    private String resolveKey() {
        UserDTO user = UserHolder.getUser();
        if (user != null) {
            return "u:" + user.getId();
        }
        return "ip:" + getClientIp();
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
