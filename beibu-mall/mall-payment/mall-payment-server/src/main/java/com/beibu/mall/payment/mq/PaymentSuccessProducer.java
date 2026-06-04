package com.beibu.mall.payment.mq;

import com.beibu.mall.payment.entity.PaymentOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessProducer {

    private static final String TOPIC = "payment-success-topic";

    private final RocketMQTemplate rocketMQTemplate;

    public void sendPaymentSuccess(PaymentOrder paymentOrder) {
        PaymentSuccessMessage message = new PaymentSuccessMessage(
                paymentOrder.getOrderId(),
                paymentOrder.getId(),
                paymentOrder.getAmount(),
                paymentOrder.getPaymentTime()
        );

        Message<PaymentSuccessMessage> mqMessage = MessageBuilder
                .withPayload(message)
                .build();

        rocketMQTemplate.syncSend(TOPIC, mqMessage);

        log.info("发送支付成功消息：orderId={}, paymentId={}, amount={}",
                message.getOrderId(), message.getPaymentId(), message.getAmount());
    }
}
