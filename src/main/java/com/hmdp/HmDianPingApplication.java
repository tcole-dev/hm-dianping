package com.hmdp;

import com.hmdp.config.EnvConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.hmdp.mapper")
@SpringBootApplication
@ServletComponentScan
@EnableScheduling
public class HmDianPingApplication {

    public static void main(String[] args) {
        // 加载 .env 文件，系统环境变量优先
        EnvConfig.load();
        SpringApplication.run(HmDianPingApplication.class, args);
    }

}
