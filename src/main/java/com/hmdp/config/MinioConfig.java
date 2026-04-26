package com.hmdp.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class MinioConfig {
    private MinioProperties minioProperties;
    public MinioConfig(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @ConfigurationProperties(prefix = "minio")
    @Data
    @Component
    public static class MinioProperties {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucketName;
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
