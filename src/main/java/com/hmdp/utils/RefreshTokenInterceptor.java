package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 刷新令牌拦截器
 * 假设当前用户访问了一些不需要拦截的路径（比如访问首页），那么登录拦截器（LoginInterceptor）就不会生效，
 * 所以此时token令牌刷新的动作实际上就不会执行
 *
 * 此拦截器拦截一切路径，用于刷新Redis中登录用户信息有效期
 *
 * @return:
 * @author: sutianyu
 * @date: 2023-01-08 22:02
 **/
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (token == null || "".equals(token)){
            return true;
        }
        //2.基于token获取redis中的用户
        String tokenKey = RedisConstants.LOGIN_USER_KEY+token;
        Map<Object,Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        //3.判断用户是否存在
        if(userMap == null || userMap.isEmpty()){
            return true;
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