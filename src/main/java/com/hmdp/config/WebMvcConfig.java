package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private LoginInterceptor loginInterceptor;
    private RefreshTokenInterceptor RefreshTokenInterceptor;
    public WebMvcConfig(LoginInterceptor loginInterceptor, RefreshTokenInterceptor refreshTokenInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.RefreshTokenInterceptor = refreshTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns("/user/code"
                        , "/user/login"
                        , "/blog/hot"
                        , "/shop/**"
                        , "/shop-type/**"
                        , "/upload/**"
                        , "/voucher/**"
                )
                .order(1);
        registry.addInterceptor(RefreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);
    }

    /**
     * CORS 跨域配置
     * 生产环境应将 allowedOriginPatterns 限制为实际域名
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // 生产环境改为具体域名，如 "https://hmdp.example.com"
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600); // 预检请求缓存1小时
    }
}