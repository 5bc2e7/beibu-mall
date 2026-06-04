package com.beibu.mall.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 支付回调请求 DTO
 *
 * 大白话：支付宝/微信付完钱后，会调用这个接口告诉我们"钱收到了"
 *
 * 回调流程：
 * 1. 用户在支付宝/微信完成支付
 * 2. 支付宝/微信发 HTTP 请求到我们的回调接口
 * 3. 我们收到请求，更新支付单状态
 * 4. 返回"成功"给支付宝/微信
 *
 * 为什么要专门写一个 DTO？
 * 因为回调的数据格式和我们内部的数据格式不一样
 * 支付宝传的是 trade_no，我们存的是 payment_no
 */
@Data
public class PaymentCallbackDTO {

    /**
     * 业务订单号（用来找到对应的支付单）
     */
    @NotBlank(message = "订单号不能为空")
    private String orderId;

    /**
     * 第三方交易号（支付宝/微信的流水号）
     */
    private String tradeNo;

    /**
     * 回调状态：SUCCESS-成功 FAIL-失败
     */
    @NotBlank(message = "回调状态不能为空")
    private String callbackStatus;
}
