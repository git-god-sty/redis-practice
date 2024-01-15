package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * redis 实现全局唯一id
 * */
@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }


}


/**
 * 全局唯一id生成策略 ： UUID, redis自增, snowflake算法, 数据库自增
 *
 *
 * 知识小贴士：关于countdownlatch
 * countdownlatch名为信号枪：主要的作用是同步协调在多线程的等待于唤醒问题
 * 我们如果没有CountDownLatch ，那么由于程序是异步的，当异步程序没有执行完时，主线程就已经执行完了，
 * 然后我们期望的是分线程全部走完之后，主线程再走，所以我们此时需要使用到CountDownLatch
 *
 * CountDownLatch 中有两个最重要的方法
 * 1、countDown
 * 2、await
 * await 方法 是阻塞方法，我们担心分线程没有执行完时，main线程就先执行，所以使用await可以让main线程阻塞，
 * 那么什么时候main线程不再阻塞呢？当CountDownLatch  内部维护的 变量变为0时，就不再阻塞，直接放行，
 * 那么什么时候CountDownLatch   维护的变量变为0 呢，
 * 我们只需要调用一次countDown ，内部变量就减少1，我们让分线程和变量绑定，
 * 执行完一个分线程就减少一个变量，当分线程全部走完，CountDownLatch 维护的变量就是0，此时await就不再阻塞，
 * 统计出来的时间也就是所有分线程执行完后的时间
 * */
