package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.hmdp.config.MinioConfig;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Component
public class MinioUtil {
    private final MinioClient minioClient;
    private final MinioConfig.MinioProperties props;

    public MinioUtil(MinioClient minioClient,
                     MinioConfig.MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    /**
     * 上传文件
     * @param file 文件
     * @return 文件名
     */
    public String uploadFile(MultipartFile file) throws Exception {
        String suffix = StrUtil.subAfter(file.getOriginalFilename(), ".", true);
        String fileName = UUID.randomUUID().toString() + "_" + UserHolder.getUser().getId() + "." + suffix;
        // 使用 minioClient 和 bucketName
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(props.getBucketName())  // ← 需要桶名
                        .object(fileName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
        return props.getEndpoint() + "/" + props.getBucketName() + "/" + fileName;
    }

    /**
     * 上传文件
     * @param file
     * @param fileName
     * @throws Exception
     */
    public String uploadFileWithName(MultipartFile file, String fileName) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(props.getBucketName())  // ← 需要桶名
                        .object(fileName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
        return props.getEndpoint() + "/" + props.getBucketName() + "/" + fileName;
    }

    /**
     * 删除文件
     * @param fileName
     * @throws Exception
     */
    public void deleteFile(String fileName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(props.getBucketName())
                        .object(fileName)
                        .build()
        );
    }
}

