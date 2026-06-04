package com.beibu.mall.seckill.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderTimeoutProducer {

    private final RocketMQTemplate rocketMQTemplate;

    private static final String TOPIC = "seckill-order-timeout-topic";

    /**
     * RocketMQ 延迟级别：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * Level 16 = 30分钟
     */
    private static final int DELAY_LEVEL_30_MINUTES = 16;

    public void sendTimeoutMessage(Long orderId, Long activityId, Long userId, long delayTime) {
        OrderTimeoutMessage message = new OrderTimeoutMessage();
        message.setOrderId(orderId);
        message.setActivityId(activityId);
        message.setUserId(userId);

        Message<OrderTimeoutMessage> msg = MessageBuilder.withPayload(message).build();
        SendResult sendResult = rocketMQTemplate.syncSend(TOPIC, msg, 3000, DELAY_LEVEL_30_MINUTES);
        log.info("订单超时消息已发送，订单ID: {}, msgId: {}", orderId, sendResult.getMsgId());
    }
}
