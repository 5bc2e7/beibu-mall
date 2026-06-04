package com.beibu.mall.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessMessage {

    private String orderId;

    private Long paymentId;

    private BigDecimal amount;

    private LocalDateTime paymentTime;
}
