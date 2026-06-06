package com.beibu.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.order.entity.OrderInfo;
import com.beibu.mall.order.entity.OrderItem;
import com.beibu.mall.order.entity.OrderStatus;
import com.beibu.mall.order.mapper.OrderInfoMapper;
import com.beibu.mall.order.mapper.OrderItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 订单事务服务测试
 *
 * 测试 OrderTransactionService 的事务逻辑，包括：
 * - 更新订单为已支付
 * - 更新订单为已取消
 * - 查询订单明细
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单事务服务测试")
class OrderTransactionServiceTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @InjectMocks
    private OrderTransactionService orderTransactionService;

    private OrderInfo pendingOrder;
    private OrderInfo paidOrder;
    private OrderInfo cancelledOrder;
    private String orderNo;

    @BeforeEach
    void setUp() {
        orderNo = "2024010100001";

        // 待支付订单
        pendingOrder = new OrderInfo();
        pendingOrder.setId(1L);
        pendingOrder.setOrderNo(orderNo);
        pendingOrder.setUserId(1001L);
        pendingOrder.setStatus(OrderStatus.PENDING_PAYMENT.getCode());

        // 已支付订单
        paidOrder = new OrderInfo();
        paidOrder.setId(2L);
        paidOrder.setOrderNo("2024010100002");
        paidOrder.setUserId(1001L);
        paidOrder.setStatus(OrderStatus.PAID.getCode());

        // 已取消订单
        cancelledOrder = new OrderInfo();
        cancelledOrder.setId(3L);
        cancelledOrder.setOrderNo("2024010100003");
        cancelledOrder.setUserId(1001L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED.getCode());
    }

    // ==================== updateOrderToPaid 测试 ====================

    @Test
    @DisplayName("更新为已支付 - 成功")
    void updateOrderToPaid_success() {
        // given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(pendingOrder);
        when(orderInfoMapper.updateById(any(OrderInfo.class)))
                .thenReturn(1);

        LocalDateTime paymentTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);

        // when
        OrderInfo result = orderTransactionService.updateOrderToPaid(orderNo, paymentTime);

        // then
        assertNotNull(result);
        assertEquals(OrderStatus.PAID.getCode(), result.getStatus());
        assertEquals(paymentTime, result.getPayTime());

        verify(orderInfoMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(orderInfoMapper, times(1)).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("更新为已支付 - 订单不存在抛出异常")
    void updateOrderToPaid_orderNotFound_throwsException() {
        // given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> orderTransactionService.updateOrderToPaid(orderNo, LocalDateTime.now()));

        assertEquals(40004, exception.getCode());
        assertTrue(exception.getMessage().contains("订单不存在"));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("更新为已支付 - 订单状态异常抛出异常")
    void updateOrderToPaid_nullStatus_throwsException() {
        // given
        pendingOrder.setStatus(null);
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(pendingOrder);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> orderTransactionService.updateOrderToPaid(orderNo, LocalDateTime.now()));

        assertEquals(40008, exception.getCode());
        assertTrue(exception.getMessage().contains("订单状态异常"));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("更新为已支付 - 已支付订单不能重复支付")
    void updateOrderToPaid_alreadyPaid_throwsException() {
        // given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(paidOrder);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> orderTransactionService.updateOrderToPaid(paidOrder.getOrderNo(), LocalDateTime.now()));

        assertEquals(40009, exception.getCode());
        assertTrue(exception.getMessage().contains("订单状态不允许支付"));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("更新为已支付 - 已取消订单不能支付")
    void updateOrderToPaid_cancelledOrder_throwsException() {
        // given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(cancelledOrder);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> orderTransactionService.updateOrderToPaid(cancelledOrder.getOrderNo(), LocalDateTime.now()));

        assertEquals(40009, exception.getCode());
        assertTrue(exception.getMessage().contains("订单状态不允许支付"));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    // ==================== updateOrderToCancelled 测试 ====================

    @Test
    @DisplayName("更新为已取消 - 成功")
    void updateOrderToCancelled_success() {
        // given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(pendingOrder);
        when(orderInfoMapper.updateById(any(OrderInfo.class)))
                .thenReturn(1);

        String reason = "超时未支付自动取消";

        // when
        OrderInfo result = orderTransactionService.updateOrderToCancelled(orderNo, reason);

        // then
        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED.getCode(), result.getStatus());
        assertNotNull(result.getCancelTime());
        assertEquals(reason, result.getCancelReason());

        verify(orderInfoMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(orderInfoMapper, times(1)).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("更新为已取消 - 订单不存在抛出异常")
    void updateOrderToCancelled_orderNotFound_throwsException() {
        // given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> orderTransactionService.updateOrderToCancelled(orderNo, "测试取消"));

        assertEquals(40004, exception.getCode());
        assertTrue(exception.getMessage().contains("订单不存在"));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("更新为已取消 - 订单状态异常抛出异常")
    void updateOrderToCancelled_nullStatus_throwsException() {
        // given
        pendingOrder.setStatus(null);
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(pendingOrder);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> orderTransactionService.updateOrderToCancelled(orderNo, "测试取消"));

        assertEquals(40008, exception.getCode());
        assertTrue(exception.getMessage().contains("订单状态异常"));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("更新为已取消 - 已支付订单不能取消")
    void updateOrderToCancelled_paidOrder_throwsException() {
        // given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(paidOrder);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> orderTransactionService.updateOrderToCancelled(paidOrder.getOrderNo(), "测试取消"));

        assertEquals(40006, exception.getCode());
        assertTrue(exception.getMessage().contains("订单状态不允许取消"));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("更新为已取消 - 已取消订单不能重复取消")
    void updateOrderToCancelled_alreadyCancelled_throwsException() {
        // given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(cancelledOrder);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> orderTransactionService.updateOrderToCancelled(cancelledOrder.getOrderNo(), "测试取消"));

        assertEquals(40006, exception.getCode());
        assertTrue(exception.getMessage().contains("订单状态不允许取消"));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    // ==================== getOrderItems 测试 ====================

    @Test
    @DisplayName("查询订单明细 - 成功返回列表")
    void getOrderItems_success() {
        // given
        OrderItem item1 = new OrderItem();
        item1.setId(1L);
        item1.setOrderNo(orderNo);
        item1.setSkuId(100L);
        item1.setQuantity(2);

        OrderItem item2 = new OrderItem();
        item2.setId(2L);
        item2.setOrderNo(orderNo);
        item2.setSkuId(200L);
        item2.setQuantity(1);

        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(item1, item2));

        // when
        List<OrderItem> result = orderTransactionService.getOrderItems(orderNo);

        // then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(100L, result.get(0).getSkuId());
        assertEquals(200L, result.get(1).getSkuId());

        verify(orderItemMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("查询订单明细 - 无明细返回空列表")
    void getOrderItems_emptyList() {
        // given
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // when
        List<OrderItem> result = orderTransactionService.getOrderItems(orderNo);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(orderItemMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }
}
