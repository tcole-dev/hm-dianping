package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogCommentsService extends IService<BlogComments> {
    Result queryCommentsByBlogId(Long blogId, Integer current);
    void saveComment(BlogComments comment);
}
