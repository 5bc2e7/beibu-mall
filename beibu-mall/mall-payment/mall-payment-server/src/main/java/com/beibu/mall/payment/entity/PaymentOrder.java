package com.beibu.mall.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体类
 *
 * 对应数据库 payment_order 表。
 *
 * 大白话：这就是一张"支付单"，记录了：
 * - 谁（userId）在什么时间（createTime）付了多少钱（amount）
 * - 付的是哪笔订单（orderId）
 * - 现在是什么状态（status）
 */
@Data
@TableName("payment_order")
public class PaymentOrder {

    /**
     * 主键ID（雪花算法生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 业务订单号（来自订单服务）
     * 这个字段有唯一索引！同一笔订单只能有一个支付单
     */
    private String orderId;

    /**
     * 支付单号（支付系统自己生成的唯一编号）
     */
    private String paymentNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 支付金额（单位：元）
     * 用 BigDecimal 而不是 double，避免精度问题
     */
    private BigDecimal amount;

    /**
     * 支付方式：1-支付宝 2-微信 3-银行卡
     */
    private Integer paymentMethod;

    /**
     * 支付状态：0-待支付 1-成功 2-失败 3-已关闭
     */
    private Integer status;

    /**
     * 第三方支付平台的交易号（如支付宝流水号）
     */
    private String tradeNo;

    /**
     * 支付成功时间
     */
    private LocalDateTime paymentTime;

    /**
     * 关闭时间
     */
    private LocalDateTime closeTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0未删除 1已删除
     */
    @TableLogic
    private Integer deleted;
}
