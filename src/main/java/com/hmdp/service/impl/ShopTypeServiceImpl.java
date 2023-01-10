package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        List<ShopType> shopTypeList = new ArrayList<>();
        //1.查询缓存
        List<String> typeList = stringRedisTemplate.opsForList().range("cache:typeList", 0,9 );
        //2.缓存存在, 返回结果
        if (typeList!=null && typeList.size()>0){
            for (String type : typeList) {
                shopTypeList.add(JSONUtil.toBean(type, ShopType.class));
            }
            return Result.ok(shopTypeList);
        }
        //3.缓存不存在，查询数据库
        shopTypeList = query().orderByAsc("sort").list();
        //4.数据库不存在，报错
        if (shopTypeList==null || shopTypeList.size()==0){
            return Result.fail("商户类型不存在");
        }
        //5.数据库存在，存入redis
        for (ShopType shopType : shopTypeList) {
            stringRedisTemplate.opsForList().rightPush("cache:typeList", JSONUtil.toJsonStr(shopType));
        }
        //6.返回结果
        return Result.ok(shopTypeList);
    }
}
