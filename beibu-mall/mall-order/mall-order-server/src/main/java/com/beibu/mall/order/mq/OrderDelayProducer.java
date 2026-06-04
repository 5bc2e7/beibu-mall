package com.beibu.mall.order.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 订单延时消息生产者
 *
 * 大白话：下单时发送一条30分钟后到达的消息，用于检查订单是否超时未支付
 *
 * 什么是延时消息？
 * 普通消息：发送后消费者立即收到
 * 延时消息：发送后要等指定时间，消费者才能收到
 *
 * 就像闹钟：你设了30分钟后的闹钟，30分钟后才会响
 *
 * 为什么用延时消息而不是定时任务？
 * 1. 精确：延时消息精确到秒，定时任务通常每分钟扫描一次
 * 2. 高效：不需要每分钟扫描整个订单表
 * 3. 可靠：消息存在MQ里，服务器重启也不会丢
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayProducer {

    private static final String TOPIC = "order-delay-check-topic";
    private static final int TIMEOUT_MS = 3000;
    private static final int DELAY_LEVEL = 16; // 30分钟
    private static final int DELAY_MINUTES = 30;

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送订单超时检查延时消息
     *
     * @param orderNo 订单号
     */
    public void sendOrderDelayMessage(String orderNo) {
        try {
            Message<String> message = MessageBuilder
                    .withPayload(orderNo)
                    .build();

            rocketMQTemplate.syncSend(TOPIC, message, TIMEOUT_MS, DELAY_LEVEL);
            log.info("订单延时消息已发送，订单号：{}，将在{}分钟后检查", orderNo, DELAY_MINUTES);
        } catch (Exception e) {
            // 延时消息是辅助功能，不应阻塞主流程
            // 超时取消由 OrderCompensationTask 定时任务兜底
            log.error("订单延时消息发送失败，订单号：{}，将由定时任务补偿", orderNo, e);
        }
    }
}
