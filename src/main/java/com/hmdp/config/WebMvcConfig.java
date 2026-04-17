package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
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
}