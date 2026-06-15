package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IBlogService blogService;

    @Override
    public Result queryCommentsByBlogId(Long blogId, Integer current) {
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    @Transactional
    public void saveComment(BlogComments comment) {
        Blog blog = blogService.getById(comment.getBlogId());
        if (blog == null) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        comment.setUserId(UserHolder.getUser().getId());
        comment.setLiked(0);
        comment.setStatus(false);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        save(comment);
        blogService.update()
                .setSql("comments = comments + 1")
                .eq("id", comment.getBlogId())
                .update();
    }
}
