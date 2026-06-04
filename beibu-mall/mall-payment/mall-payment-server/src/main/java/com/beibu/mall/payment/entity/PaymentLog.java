package com.beibu.mall.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付流水日志实体类
 *
 * 对应数据库 payment_log 表。
 *
 * 大白话：这就是一张"操作日志"，记录了每一次支付状态的变更。
 * 比如：
 * - 创建支付单 → 记录一条
 * - 收到支付回调 → 记录一条
 * - 超时关闭 → 记录一条
 *
 * 有什么用？
 * - 对账：和支付宝/微信的账单对比
 * - 排查问题：用户说"我付了钱但订单没变"，查日志就知道回调有没有到
 */
@Data
@TableName("payment_log")
public class PaymentLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的支付单ID */
    private Long paymentId;

    /** 关联的业务订单号 */
    private String orderId;

    /** 操作类型：CREATE-创建 CALLBACK-回调 CLOSE-关闭 REFUND-退款 */
    private String operation;

    /** 操作前的状态 */
    private Integer beforeStatus;

    /** 操作后的状态 */
    private Integer afterStatus;

    /** 操作说明 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
