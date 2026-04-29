package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String code = RandomUtil.randomNumbers(6);

        session.setAttribute("code", code);

        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok("发送成功");
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String sessionCode = (String) session.getAttribute("code");
        if (sessionCode == null || !sessionCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }

        User user = query().eq("phone", phone).one();

        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            save(user);
        }
        
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setNickName(user.getNickName());
        userDTO.setIcon(user.getIcon());
        String token = UUID.randomUUID().toString() + ":" + user.getId();
        var map = BeanUtil.beanToMap(userDTO, new HashMap<String, Object>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((name, value) -> value.toString()));
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String json = objectMapper.writeValueAsString(userDTO);
            stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_USER_KEY + token, json);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return Result.fail("登录失败");
        }
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        String key = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // Redis中bitmap的key
        String key = RedisConstants.USER_SIGN_KEY + userId + date;
        // 本月的第几天
        int day = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // Redis中bitmap的key
        String key = RedisConstants.USER_SIGN_KEY + userId + date;
        // 本月的第几天
        int day = now.getDayOfMonth();
        // 获取本月的签到数据
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
                //      .get(u5).valueAt(0)
                //      .get(u6).valueAt(0)     .create()可同时操作多段，所以返回值为List
        );
        // 返回的是bit串转化成的十进制，如bitmap中是：00101，即返回 101对应的十进制数5
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        Long signCount = result.get(0);
        if (signCount == null || signCount == 0) {
            return Result.ok(0);
        }

        int ans = 0;
        // 若该十进制数与1相与得1，说明最低位为1，否则为0，由此判断该天是否签到。
        // 循环右移，判断每一天是否签到
        while ((signCount & 1) == 1) {
            ans++;
            signCount >>= 1;
        }
        return Result.ok(ans);
    }
}
