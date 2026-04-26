package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.MinioUtil;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String suffix = StrUtil.subAfter(originalFilename, ".", true);
            String fileName = UUID.randomUUID().toString() + "_" + UserHolder.getUser().getId() + "." + suffix;

//            // 保存文件
//            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
//            // 返回结果
//            log.debug("文件上传成功，{}", fileName);

            String url = minioUtil.uploadFileWithName(image, fileName);

            return Result.ok(url);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.fail("上传文件失败");
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        try {
            minioUtil.deleteFile(filename);
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return Result.fail("文件删除失败");
        }
        return Result.ok();
    }

    /*
    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
    */
}
