package com.beibu.mall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀订单 Mapper 接口
 *
 * 继承 BaseMapper，自动拥有 CRUD 能力
 * 订单表有唯一索引 uk_user_activity(user_id, activity_id)
 * 如果插入重复数据，数据库会抛 DuplicateKeyException
 */
@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    // 自定义查询方法可以在这里扩展
    // 比如：根据用户ID查询所有秒杀订单
}
