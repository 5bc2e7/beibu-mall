package com.beibu.mall.order.mq;

import com.beibu.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时检查消费者
 *
 * 大白话：接收30分钟前发送的延时消息，检查订单是否还未支付
 *
 * 工作流程：
 * 1. 用户下单时，订单服务发送一条延时消息（30分钟后到达）
 * 2. 30分钟后，RocketMQ 把消息投递给这个消费者
 * 3. 消费者检查订单状态，如果还是"待支付"，就自动取消
 *
 * 为什么要检查订单状态？
 * 因为用户可能在30分钟内已经支付了，这时候就不能取消了
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "order-delay-check-topic",
    consumerGroup = "order-delay-check-group"
)
public class OrderDelayConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    /**
     * 处理延时消息
     *
     * @param orderNo 订单号（消息体就是订单号）
     */
    @Override
    public void onMessage(String orderNo) {
        log.info("收到订单超时检查消息，订单号：{}", orderNo);

        try {
            // 调用订单服务的超时取消方法
            orderService.cancelTimeoutOrder(orderNo);
            log.info("订单超时检查完成，订单号：{}", orderNo);
        } catch (Exception e) {
            log.error("订单超时检查失败，订单号：{}", orderNo, e);
            // 抛出异常让 RocketMQ 重试
            throw e;
        }
    }
}
