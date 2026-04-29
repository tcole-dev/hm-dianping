package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.LargeDataThreadPool;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    private Executor largeDataThreadPool;
    private IFollowService followService;
    private StringRedisTemplate stringRedisTemplate;
    private IUserService iUserService;
    public BlogServiceImpl(
            @Qualifier("getExecutor") Executor largeDataThreadPool,
            IFollowService followService,
            StringRedisTemplate stringRedisTemplate,
            IUserService iUserService
    ) {
        this.largeDataThreadPool = largeDataThreadPool;
        this.followService = followService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.iUserService = iUserService;
    }
    // 重写save方法，在保存blog同时，将其推送到关注者的收件箱
    @Override
    public Result saveBlog(Blog blog) {
        if (!save(blog)) {
            return Result.fail("保存失败");
        }

        largeDataThreadPool.execute(() -> {
            // 1.查询当前blog的作者的所有粉丝
            List<Follow> follows = followService.query()
                    .eq("follow_id", blog.getUserId()).list();
            // 2.把blog放入收件箱
            for (Follow follow : follows) {
                Long followerId = follow.getUserId();
                String key = RedisConstants.FEED_KEY + followerId;
                stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
            }
        });

        return Result.ok(blog.getId());
    }

    /**
     * 查询当前用户所关注的人的最新blog
     * @param max 最大时间（即上次查询到的最小时间戳）
     * @param offset 偏移量（同一时间戳可能有多条数据，使用offset表示偏移）
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        // 查询收件箱
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String> > typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 判空处理
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 用于存放blog的Id
        var Ids = new ArrayList<Long>(typedTuples.size());
        long minTime = 0;   // 本次查询到的最小时间戳
        long temp = 0;      // 临时变量
        int cnt = 0;        // 最小时间戳的个数
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Ids.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));
            temp = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (minTime == temp) cnt++;
            else {
                minTime = temp;
                cnt = 1;
            }
        }
        // 若minTime == max，则说明本次查询的blog中，其实是在查询上次剩下的那个时间戳的数据，需要将offset加上cnt
        cnt = minTime == max ? offset + cnt : cnt;

        String idStr = StrUtil.join(",", Ids);
        List<Blog> blogList = query().in("id", Ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();

        for (Blog blog : blogList) {
            completeBlog(blog);
            checkBlogLike(blog);
        }
        var ans = new ScrollResult();
        ans.setList(blogList);
        ans.setMinTime(minTime);
        ans.setOffset(cnt);
        return Result.ok(ans);
    }

    // 补全blog中的用户信息
    private void completeBlog (Blog blog) {
        User user = iUserService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        blog.setName(user.getNickName());
    }

    // 检查blog中的点赞信息
    private void checkBlogLike(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
