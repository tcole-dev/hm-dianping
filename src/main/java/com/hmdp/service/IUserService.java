package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.entity.User;

import javax.servlet.http.HttpServletRequest;

public interface IUserService extends IService<User> {
    void sendCode(String phone);
    String login(LoginFormDTO loginForm);
    void logout(HttpServletRequest request);
    void sign();
    Integer signCount();
}
