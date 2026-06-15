package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.exception.ErrorCode;
import com.hmdp.utils.MinioUtil;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {
    private final MinioUtil minioUtil;
    public UploadController(MinioUtil minioUtil) {
        this.minioUtil = minioUtil;
    }

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            String originalFilename = image.getOriginalFilename();
            String suffix = StrUtil.subAfter(originalFilename, ".", true);
            String fileName = UUID.randomUUID().toString() + "_" + UserHolder.getUser().getId() + "." + suffix;
            String url = minioUtil.uploadFileWithName(image, fileName);
            return Result.ok(url);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.fail(ErrorCode.UPLOAD_FAIL);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        try {
            minioUtil.deleteFile(filename);
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return Result.fail(ErrorCode.FILE_DELETE_FAIL);
        }
        return Result.ok();
    }
}
