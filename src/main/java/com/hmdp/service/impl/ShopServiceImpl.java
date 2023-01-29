package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopById(Long id) {
        Shop shop = null;
        //解决缓存穿透
        //shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        shop = queryWithMutex(id);

        //返回信息
        if (shop == null){
            return Result.fail("商户不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 解决缓存穿透
     * */
    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //0.从redis中查询商铺缓存 (是以string类型json格式存储的)
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //1.判断是否存在
        if (shopJson != null && !"".equals(shopJson)){
            //2.缓存中存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if ("".equals(shopJson)){
            //解决缓存穿透-- 2.缓存的是空值，返回null
            return null;
        }
        //3.缓存中不存在，查询数据库
        Shop shop = getById(id);
        //4.数据库中不存在，报错
        if (shop==null){
            //解决缓存穿透-- 1.数据库中不存在，缓存空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回null
            return null;
        }
        //5.数据库中存在，查询出来缓存到redis中 (是以string类型json格式存储的)
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.返回信息
        return shop;
    }

    /** 互斥锁解决缓存击穿 begin */
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //0.从redis中查询商铺缓存 (是以string类型json格式存储的)
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //1.判断是否存在
        if (shopJson != null && !"".equals(shopJson)){
            //2.缓存中存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if ("".equals(shopJson)){
            //解决缓存穿透-- 2.缓存的是空值，返回null
            return null;
        }
        //3 缓存中不存在,实现缓存重构
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try{
            //3.1 尝试获取互斥锁
            boolean islock = tryLock(lockKey);
            if (!islock){
                //3.2 未取到锁，休眠一段时间后重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //3.3 成功获取锁，查询数据库
            shop = getById(id);
            //4.数据库中不存在，报错
            if (shop == null){
                //解决缓存穿透-- 1.数据库中不存在，缓存空值
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回null
                return null;
            }
            //5.数据库中存在，查询出来缓存到redis中 (是以string类型json格式存储的)
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch(Exception e){
            throw new RuntimeException(e);
        }finally{
            unlock(lockKey);
        }
        //6.返回信息
        return shop;
    }

    //获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
    /** 互斥锁解决缓存击穿 end */


    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("商户id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}


/**
 * 缓存雪崩是指在同一时段大量的缓存key同时失效或者Redis服务宕机，导致大量请求到达数据库，带来巨大压力。
 *
 * 解决方案：
 * * 给不同的Key的TTL添加随机值
 * * 利用Redis集群提高服务的可用性
 * * 给缓存业务添加降级限流策略
 * * 给业务添加多级缓存
 * */

/**
 * 缓存穿透 ：缓存穿透是指客户端请求的数据在缓存中和数据库中都不存在，这样缓存永远不会生效，这些请求都会打到数据库。
 *
 * 常见的解决方案有两种：
 * * 缓存空对象
 *     * 优点：实现简单，维护方便
 *     * 缺点：
 *         * 额外的内存消耗
 *         * 可能造成短期的不一致
 * * 布隆过滤
 *     * 优点：内存占用较少，没有多余key
 *     * 缺点：
 *         * 实现复杂
 *         * 存在误判可能
 * */

/**
 * 缓存击穿问题也叫热点Key问题，就是一个被高并发访问并且缓存重建业务较复杂的key突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击。
 *
 * 常见的解决方案有两种：
 * * 互斥锁
 * * 逻辑过期
 * */
