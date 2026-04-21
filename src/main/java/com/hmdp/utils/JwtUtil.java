package com.hmdp.utils;

import com.hmdp.config.JwtConfig;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Deprecated
@Component
public class JwtUtil {
    @Autowired
    private static JwtConfig jwtConfig;

    /**
     * 生成token
     * @param claims
     * @return
     */
    public static String getToken(Map<String, Object>  claims) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        Date now = new Date(jwtConfig.getTtl());
        JwtBuilder jwtBuilder = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + jwtConfig.getTtl()))
                .signWith(signatureAlgorithm, jwtConfig.getSecret().getBytes());
        return jwtBuilder.compact();
    }

    /**
     * 解析token
     * @param token
     * @return
     */
    public Map<String, Object> parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(jwtConfig.getSecret().getBytes())
                .parseClaimsJws(token)
                .getBody();
    }
}
