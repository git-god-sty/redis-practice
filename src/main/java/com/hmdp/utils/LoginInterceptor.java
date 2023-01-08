package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 登录拦截器
 * 用于拦截 订单、我的信息 等需要登录后才可以访问的请求
 *
 * @className: LoginInterceptor
 * @author: sutianyu
 * @date: 2022-12-27 17:36
 **/
public class LoginInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null){
            //用户未登录，需要拦截，设置401状态码，拦截
            response.setStatus(401);
            return false;
        }
        //有用户，放行
        return true;
    }
}