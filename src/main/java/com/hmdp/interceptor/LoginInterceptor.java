package com.hmdp.interceptor;

import com.hmdp.config.JwtConfig;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {
    private static final List<String> nonAuthPaths = Arrays.asList(
        "/shop",
        "/voucher",
        "/shop-type",
        "/upload",
        "/blog/hot",
        "/user/code",
        "/user/login"
    );

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtConfig jwtConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();

        for (String path : nonAuthPaths) {
            if (requestURI.startsWith(path)) {
                return true;
            }
        }

        // Session校验
        HttpSession session = request.getSession();
        Object userObj = session.getAttribute("user");
        if (userObj != null) {
            UserDTO userDTO;
            if (userObj instanceof UserDTO) {
                userDTO = (UserDTO) userObj;
            } else {
                com.hmdp.entity.User user = (com.hmdp.entity.User) userObj;
                userDTO = new UserDTO();
                userDTO.setId(user.getId());
                userDTO.setNickName(user.getNickName());
                userDTO.setIcon(user.getIcon());
            }
            UserHolder.saveUser(userDTO);
            return true;
        }
        log.debug("登录拦截");
        response.setStatus(401);
        return false;


        // Token校验（暂不启用，用于升级）
//        String token = request.getHeader(jwtConfig.getTokenName());
//        if (token == null) {
//            response.setStatus(502);
//            return false;
//        }
//
//        Map<String, Object> claims = jwtUtil.parseToken(token);
//        if (claims == null) {
//            response.setStatus(502);
//            return false;
//        }
//        BaseContext.setCurrentId((Long) claims.get("id"));
//
//        return true;
    }
}