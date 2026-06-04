package com.beibu.mall.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付详情 VO
 *
 * VO = View Object（视图对象）
 * 大白话：返回给前端展示的"数据包裹"
 *
 * 为什么需要 VO？
 * 1. 安全：不暴露数据库实体的敏感字段（如 deleted）
 * 2. 灵活：可以组合多个表的字段
 * 3. 稳定：数据库表结构变化时，VO 保持不变
 */
@Data
public class PaymentVO {

    /** 支付单ID */
    private Long id;

    /** 业务订单号 */
    private String orderId;

    /** 支付单号 */
    private String paymentNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付方式描述 */
    private String paymentMethodDesc;

    /**
     * 支付状态
     * 0-待支付 1-成功 2-失败 3-已关闭
     */
    private Integer status;

    /** 支付状态描述（前端直接展示） */
    private String statusDesc;

    /** 支付成功时间 */
    private LocalDateTime paymentTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
