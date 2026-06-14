package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 根据博客id分页查询评论
     * @param blogId 博客id
     * @param current 页码
     * @return 评论列表
     */
    @GetMapping("/{blogId}")
    public Result queryCommentsByBlogId(
            @PathVariable("blogId") Long blogId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return blogCommentsService.queryCommentsByBlogId(blogId, current);
    }

    /**
     * 发表评论
     * @param comment 评论内容
     * @return 结果
     */
    @PostMapping
    public Result saveComment(@Valid @RequestBody BlogComments comment) {
        return blogCommentsService.saveComment(comment);
    }
}
