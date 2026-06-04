package com.beibu.mall.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.payment.entity.PaymentLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付流水日志 Mapper 接口
 *
 * 大白话：这个接口负责操作 payment_log 表
 * 记录每一次支付状态的变更，用于对账和问题排查
 */
@Mapper
public interface PaymentLogMapper extends BaseMapper<PaymentLog> {
    // 继承 BaseMapper 已经提供了基本的 CRUD 方法
}
