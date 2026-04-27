package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private FollowMapper followMapper;
    private StringRedisTemplate stringRedisTemplate;
    private IUserService userService;
    public FollowServiceImpl(
            FollowMapper followMapper,
            StringRedisTemplate stringRedisTemplate,
            IUserService userService) {
        this.followMapper = followMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    /**
     * 关注或取消关注
     * @param followId
     * @param isFollow，true为关注，false为取消关注
     * @return
     */
    @Override
    public Result follow(Long followId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            // 保存到数据库
            if (save(follow)) {
                stringRedisTemplate.opsForSet().add(key, followId.toString());
            } else {
                return Result.fail("关注失败");
            }
        } else {
            // 数据库移除关注
            followMapper.removeFollow(userId, followId);
            // Redis移除关注
            stringRedisTemplate.opsForSet().remove(key, followId.toString());
        }
        return Result.ok();
    }

    /**
     * 判断是否关注
     * @param followId
     * @return
     */
    @Override
    public Result isFollow(Long followId) {
        Long userId = UserHolder.getUser().getId();
//        Long count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        String key = "follow:" + userId;

        return Result.ok(stringRedisTemplate.opsForSet().isMember(key, followId.toString()));
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String curUserKey = "follow:" + userId;
        String targetUserKey = "follow:" + id;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(curUserKey, targetUserKey);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids).stream().map(user -> {
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setIcon(user.getIcon());
            userDTO.setNickName(user.getNickName());
            return userDTO;
        }).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }


}
