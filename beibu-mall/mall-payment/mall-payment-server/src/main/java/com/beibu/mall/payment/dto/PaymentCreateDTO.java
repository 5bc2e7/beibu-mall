package com.beibu.mall.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建支付单请求 DTO
 *
 * DTO = Data Transfer Object（数据传输对象）
 * 大白话：前端发过来的"请求包裹"，只包含创建支付单需要的数据
 *
 * 为什么要用 DTO，不直接用实体类？
 * 1. 安全：实体类有 id、status 等字段，不应该让前端传
 * 2. 灵活：DTO 可以加校验注解（@NotBlank），实体类不方便
 * 3. 解耦：前端传什么和数据库存什么，是两回事
 */
@Data
public class PaymentCreateDTO {

    /**
     * 业务订单号（来自订单服务）
     * @NotBlank 表示不能为空字符串
     */
    @NotBlank(message = "订单号不能为空")
    private String orderId;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 支付金额
     * @DecimalMin 最小值为0.01，不能付0元或负数
     */
    @NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0.01", message = "支付金额必须大于0")
    private BigDecimal amount;

    /**
     * 支付方式：1-支付宝 2-微信 3-银行卡
     */
    @NotNull(message = "支付方式不能为空")
    private Integer paymentMethod;
}
