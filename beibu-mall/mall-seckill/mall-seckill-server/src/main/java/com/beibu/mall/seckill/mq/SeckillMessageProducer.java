package com.beibu.mall.seckill.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillMessageProducer {

    private final RocketMQTemplate rocketMQTemplate;

    private static final String TOPIC = "seckill-topic";

    public void sendMessage(SeckillMessage message) {
        SendResult sendResult = rocketMQTemplate.syncSend(TOPIC, message);
        if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new RuntimeException("MQ消息发送状态异常: " + sendResult.getSendStatus());
        }
        log.info("消息发送成功，活动ID: {}, 用户ID: {}, msgId: {}",
                message.getActivityId(), message.getUserId(), sendResult.getMsgId());
    }
}
