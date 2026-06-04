package com.beibu.mall.order.mq;

import com.beibu.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功消息消费者
 *
 * 大白话：监听支付服务发来的"支付成功"消息，然后更新订单状态
 *
 * 为什么用 MQ 而不是直接调用？
 * 1. 解耦：支付服务不需要知道订单服务的接口
 * 2. 可靠：消息存在 MQ 里，即使订单服务挂了也不会丢
 * 3. 异步：支付服务不用等订单服务处理完才返回
 *
 * @RocketMQMessageListener 参数解释：
 * - topic: 消息主题，相当于"频道名"，支付服务和订单服务约定好同一个频道
 * - consumerGroup: 消费者组，同一组内的消费者会竞争消费消息
 *   （集群模式：一条消息只被组内一个消费者处理）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "payment-success-topic",
    consumerGroup = "order-payment-success-group"
)
public class PaymentSuccessConsumer implements RocketMQListener<PaymentSuccessMessage> {

    private final OrderService orderService;

    /**
     * 处理支付成功消息
     *
     * 当支付服务发送消息后，RocketMQ 会调用这个方法
     * 我们在这里更新订单状态，并通知库存服务确认扣减
     */
    @Override
    public void onMessage(PaymentSuccessMessage message) {
        log.info("收到支付成功消息：orderId={}, paymentId={}, amount={}",
                message.getOrderId(), message.getPaymentId(), message.getAmount());

        try {
            // 调用订单服务处理支付成功逻辑
            orderService.handlePaymentSuccess(
                    message.getOrderId(),
                    message.getPaymentId(),
                    message.getAmount(),
                    message.getPaymentTime()
            );
            log.info("支付成功消息处理完成：orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("支付成功消息处理失败：orderId={}", message.getOrderId(), e);
            // 抛出异常让 RocketMQ 重试（默认重试16次）
            throw e;
        }
    }
}
