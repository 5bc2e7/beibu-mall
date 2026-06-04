package com.beibu.mall.payment.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付成功消息体
 *
 * 大白话：支付成功后，通过 RocketMQ 发送给订单服务的消息
 *
 * 消息内容包含：
 * - orderId：订单服务需要知道是哪笔订单付了钱
 * - paymentId：支付单ID，方便追溯
 * - amount：支付金额，订单服务可以做校验
 * - paymentTime：支付时间，订单服务需要记录
 *
 * 为什么不直接传 PaymentOrder 实体？
 * 1. 实体类包含很多不需要的字段（如 deleted）
 * 2. 消息体应该尽量小，减少网络传输
 * 3. 解耦：消息格式独立于数据库表结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessMessage {

    /** 业务订单号 */
    private String orderId;

    /** 支付单ID */
    private Long paymentId;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付时间 */
    private LocalDateTime paymentTime;
}
