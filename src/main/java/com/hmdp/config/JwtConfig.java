package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.jwt")
public class JwtConfig {
    private String secret;
    private long ttl;
    private String tokenName;
}
