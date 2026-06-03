package com.beibu.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单商品明细 Mapper 接口
 *
 * 作用：操作 order_item 表
 * 继承 BaseMapper 获得基本的 CRUD 能力
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
    // 继承 BaseMapper 已经提供了基本的 CRUD 方法
}
