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
 *
 * @className: LoginInterceptor
 * @author: sutianyu
 * @date: 2022-12-27 17:36
 **/
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;

    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (token == null || "".equals(token)){
            //token不存在， 拦截，返回401
            response.setStatus(401);
            return false;
        }
        //2.基于token获取redis中的用户
        String tokenKey = RedisConstants.LOGIN_USER_KEY+token;
        Map<Object,Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        //3.判断用户是否存在
        if(userMap == null || userMap.isEmpty()){
            //4.不存在，拦截，返回401状态码
            response.setStatus(401);
            return false;
        }
        //5.存在，将查询到的Hash数据转化为UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6.保存用户信息到Threadlocal
        UserHolder.saveUser(userDTO);
        //7.刷新token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);
        //8.放行
        return true;
    }
}