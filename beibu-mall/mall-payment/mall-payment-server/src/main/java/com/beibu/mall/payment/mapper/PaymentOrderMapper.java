package com.beibu.mall.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付订单 Mapper 接口
 *
 * 大白话：这个接口负责操作 payment_order 表
 *
 * 继承 BaseMapper 后，自动拥有这些方法：
 * - insert：插入一条记录
 * - selectById：根据ID查询
 * - updateById：根据ID更新
 * - selectList：查询列表
 * - ...等等
 *
 * 这些方法不用我们写 SQL，MyBatis-Plus 自动生成！
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
    // 继承 BaseMapper 已经提供了基本的 CRUD 方法
}
