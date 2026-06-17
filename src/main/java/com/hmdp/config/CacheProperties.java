package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


// 二级缓存配置（L1 Caffeine + L2 Redis）
@Data
@Component
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {
    /** L1 本地缓存最大条目数 */
    private int maxSize = 1000;
    /** L1 本地缓存 TTL（秒） */
    private long ttl = 30;
}
