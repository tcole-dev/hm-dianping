package com.hmdp.service.impl;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
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

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private FollowMapper followMapper;
    private StringRedisTemplate stringRedisTemplate;
    private IUserService userService;

    public FollowServiceImpl(FollowMapper followMapper, StringRedisTemplate stringRedisTemplate, IUserService userService) {
        this.followMapper = followMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    @Override
    public void follow(Long followId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        if (isFollow) {
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, followId.toString());
            if (Boolean.TRUE.equals(isMember)) {
                return;
            }
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            if (save(follow)) {
                stringRedisTemplate.opsForSet().add(key, followId.toString());
            } else {
                throw new BusinessException(ErrorCode.FOLLOW_FAIL);
            }
        } else {
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, followId.toString());
            if (!Boolean.TRUE.equals(isMember)) {
                return;
            }
            followMapper.removeFollow(userId, followId);
            stringRedisTemplate.opsForSet().remove(key, followId.toString());
        }
    }

    @Override
    public Boolean isFollow(Long followId) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, followId.toString()));
    }

    @Override
    public List<UserDTO> followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String curUserKey = "follow:" + userId;
        String targetUserKey = "follow:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(curUserKey, targetUserKey);
        if (intersect == null || intersect.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        return ids.stream().map(uid -> {
            User user = userService.queryById(uid);
            if (user == null) return null;
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setIcon(user.getIcon());
            userDTO.setNickName(user.getNickName());
            return userDTO;
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }
}
