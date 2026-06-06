package com.beibu.mall.payment.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.payment.dto.PaymentCallbackDTO;
import com.beibu.mall.payment.dto.PaymentCreateDTO;
import com.beibu.mall.payment.enums.PaymentStatus;
import com.beibu.mall.payment.service.PaymentService;
import com.beibu.mall.payment.vo.PaymentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private PaymentVO buildPaymentVO() {
        PaymentVO vo = new PaymentVO();
        vo.setId(1L);
        vo.setOrderId("ORDER_20240101_001");
        vo.setPaymentNo("PAY_ABC123DEF456");
        vo.setAmount(new BigDecimal("199.80"));
        vo.setStatus(PaymentStatus.PENDING.getCode());
        vo.setStatusDesc("待支付");
        vo.setPaymentMethodDesc("支付宝");
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    @Test
    @DisplayName("创建支付单 - 返回成功Result")
    void createPayment_success() {
        PaymentCreateDTO dto = new PaymentCreateDTO();
        dto.setOrderId("ORDER_20240101_001");
        dto.setUserId(1001L);
        dto.setAmount(new BigDecimal("199.80"));
        dto.setPaymentMethod(1);

        when(paymentService.createPayment(any(PaymentCreateDTO.class)))
                .thenReturn(buildPaymentVO());

        Result<PaymentVO> result = paymentController.createPayment(dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("ORDER_20240101_001", result.getData().getOrderId());
        assertEquals(new BigDecimal("199.80"), result.getData().getAmount());
    }

    @Test
    @DisplayName("支付回调 - 返回成功Result")
    void handleCallback_success() {
        PaymentCallbackDTO dto = new PaymentCallbackDTO();
        dto.setOrderId("ORDER_20240101_001");
        dto.setTradeNo("ALIPAY_TX_001");
        dto.setCallbackStatus("SUCCESS");

        PaymentVO successVO = buildPaymentVO();
        successVO.setStatus(PaymentStatus.SUCCESS.getCode());
        successVO.setStatusDesc("支付成功");

        when(paymentService.handleCallback(any(PaymentCallbackDTO.class)))
                .thenReturn(successVO);

        Result<PaymentVO> result = paymentController.handleCallback(dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(PaymentStatus.SUCCESS.getCode(), result.getData().getStatus());
        assertEquals("支付成功", result.getData().getStatusDesc());
    }

    @Test
    @DisplayName("查询支付单 - 返回成功Result")
    void getPaymentByOrderId_success() {
        when(paymentService.getPaymentByOrderId(eq("ORDER_20240101_001")))
                .thenReturn(buildPaymentVO());

        Result<PaymentVO> result = paymentController.getPaymentByOrderId("ORDER_20240101_001");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("ORDER_20240101_001", result.getData().getOrderId());
        assertEquals("PAY_ABC123DEF456", result.getData().getPaymentNo());
    }
}
