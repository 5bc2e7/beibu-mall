package com.beibu.mall.payment.mq;

import com.beibu.mall.payment.entity.PaymentOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 支付成功消息生产者
 *
 * 大白话：支付成功后，通过 RocketMQ 发消息给订单服务
 *
 * 为什么用 MQ 而不是直接调用订单服务（Feign）？
 * 1. 解耦：支付服务不需要知道订单服务的接口
 * 2. 可靠：消息存在 MQ 里，即使订单服务挂了也不会丢
 * 3. 异步：支付服务不用等订单服务处理完才返回
 *
 * 这个类目前是 Mock 实现，实际项目中需要接入 RocketMQ
 */
@Slf4j
@Component
public class PaymentSuccessProducer {

    /**
     * 发送支付成功消息
     *
     * @param paymentOrder 支付单信息
     */
    public void sendPaymentSuccess(PaymentOrder paymentOrder) {
        // 构建消息体
        PaymentSuccessMessage message = new PaymentSuccessMessage(
                paymentOrder.getOrderId(),
                paymentOrder.getId(),
                paymentOrder.getAmount(),
                paymentOrder.getPaymentTime()
        );

        // 实际项目中这里会调用 rocketMQTemplate.convertAndSend()
        // 目前先打印日志，模拟发送成功
        log.info("【MQ】发送支付成功消息：orderId={}, paymentId={}, amount={}",
                message.getOrderId(), message.getPaymentId(), message.getAmount());

        // TODO: 接入 RocketMQ 后替换为真实发送
        // rocketMQTemplate.convertAndSend("payment-success-topic", message);
    }
}
