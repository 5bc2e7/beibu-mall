package com.beibu.mall.seckill.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillProducerTest {

    @Mock
    private RocketMQTemplate template;

    @InjectMocks
    private SeckillMessageProducer seckillProducer;

    @InjectMocks
    private OrderTimeoutProducer orderTimeoutProducer;

    private SendResult createSendResult(SendStatus status, String msgId) {
        return new SendResult(status, msgId, msgId, null, 0);
    }

    @Test
    void sendMessage_success() {
        SeckillMessage message = new SeckillMessage();
        message.setActivityId(1L);
        message.setUserId(100L);
        message.setToken("abc123");

        SendResult sendResult = createSendResult(SendStatus.SEND_OK, "msgId123");
        when(template.syncSend(eq("seckill-topic"), eq(message))).thenReturn(sendResult);

        assertDoesNotThrow(() -> seckillProducer.sendMessage(message));

        verify(template).syncSend("seckill-topic", message);
    }

    @Test
    void sendMessage_fail_throwsException() {
        SeckillMessage message = new SeckillMessage();
        message.setActivityId(1L);
        message.setUserId(100L);

        SendResult sendResult = createSendResult(SendStatus.FLUSH_DISK_TIMEOUT, "msgId456");
        when(template.syncSend(eq("seckill-topic"), eq(message))).thenReturn(sendResult);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> seckillProducer.sendMessage(message));
        assertTrue(exception.getMessage().contains("MQ消息发送状态异常"));
        verify(template).syncSend("seckill-topic", message);
    }

    @Test
    void sendTimeoutMessage_success() {
        Long orderId = 1001L;
        Long activityId = 1L;
        Long userId = 100L;

        SendResult sendResult = createSendResult(SendStatus.SEND_OK, "msgId789");
        when(template.syncSend(eq("seckill-order-timeout-topic"), any(), eq(3000L), eq(16)))
                .thenReturn(sendResult);

        assertDoesNotThrow(() -> orderTimeoutProducer.sendTimeoutMessage(orderId, activityId, userId, 1800000L));

        verify(template).syncSend(eq("seckill-order-timeout-topic"), any(), eq(3000L), eq(16));
    }
}
