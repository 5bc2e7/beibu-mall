package com.beibu.mall.order.compensation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.inventory.api.dto.StockOperationDTO;
import com.beibu.mall.inventory.api.feign.InventoryFeignClient;
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
 * 订单补偿任务测试
 *
 * 测试 OrderCompensationTask 的补偿逻辑，包括：
 * - 无待补偿订单时跳过
 * - 单订单补偿成功
 * - 部分库存释放失败时重试
 * - 全部成功后标记 [已补偿]
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单补偿任务测试")
@SuppressWarnings("unchecked")  // LambdaQueryWrapper 是泛型，Mockito.any() 无法推断类型
class OrderCompensationTaskTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private InventoryFeignClient inventoryFeignClient;

    @InjectMocks
    private OrderCompensationTask compensationTask;

    private OrderInfo cancelledOrder;
    private OrderItem orderItem1;
    private OrderItem orderItem2;

    @BeforeEach
    void setUp() {
        // 已取消订单
        cancelledOrder = new OrderInfo();
        cancelledOrder.setId(1L);
        cancelledOrder.setOrderNo("2024010100001");
        cancelledOrder.setUserId(1001L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED.getCode());
        cancelledOrder.setCancelTime(LocalDateTime.now().minusHours(1));
        cancelledOrder.setCancelReason("用户主动取消");

        // 订单明细1
        orderItem1 = new OrderItem();
        orderItem1.setId(1L);
        orderItem1.setOrderId(1L);
        orderItem1.setOrderNo("2024010100001");
        orderItem1.setSkuId(100L);
        orderItem1.setQuantity(2);

        // 订单明细2
        orderItem2 = new OrderItem();
        orderItem2.setId(2L);
        orderItem2.setOrderId(1L);
        orderItem2.setOrderNo("2024010100001");
        orderItem2.setSkuId(200L);
        orderItem2.setQuantity(1);
    }

    // ==================== compensateCancelledOrderStock 测试 ====================

    @Test
    @DisplayName("补偿任务 - 无待补偿订单时跳过")
    void compensateCancelledOrderStock_noOrders_skips() {
        // given
        when(orderInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // when
        compensationTask.compensateCancelledOrderStock();

        // then
        verify(orderInfoMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
        verify(orderItemMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(inventoryFeignClient, never()).releaseStock(any(StockOperationDTO.class));
    }

    @Test
    @DisplayName("补偿任务 - 单订单全部库存释放成功")
    void compensateCancelledOrderStock_allSuccess_marksCompensated() {
        // given
        List<OrderInfo> orders = Arrays.asList(cancelledOrder);
        when(orderInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(orders);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(orderItem1, orderItem2));
        when(inventoryFeignClient.releaseStock(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());
        when(orderInfoMapper.updateById(any(OrderInfo.class)))
                .thenReturn(1);

        // when
        compensationTask.compensateCancelledOrderStock();

        // then
        verify(inventoryFeignClient, times(2)).releaseStock(any(StockOperationDTO.class));
        verify(orderInfoMapper, times(1)).updateById(any(OrderInfo.class));
        assertTrue(cancelledOrder.getCancelReason().contains("[已补偿]"));
    }

    @Test
    @DisplayName("补偿任务 - 部分库存释放失败不标记补偿")
    void compensateCancelledOrderStock_partialFailure_noMark() {
        // given
        List<OrderInfo> orders = Arrays.asList(cancelledOrder);
        when(orderInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(orders);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(orderItem1, orderItem2));

        // 第一个成功，第二个失败
        when(inventoryFeignClient.releaseStock(argThat(dto ->
                dto != null && "100".equals(dto.getSkuId()))))
                .thenReturn(Result.ok());
        when(inventoryFeignClient.releaseStock(argThat(dto ->
                dto != null && "200".equals(dto.getSkuId()))))
                .thenReturn(Result.fail(10031, "库存不足"));

        // when
        compensationTask.compensateCancelledOrderStock();

        // then
        verify(inventoryFeignClient, times(2)).releaseStock(any(StockOperationDTO.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        assertFalse(cancelledOrder.getCancelReason().contains("[已补偿]"));
    }

    @Test
    @DisplayName("补偿任务 - 库存服务异常不标记补偿")
    void compensateCancelledOrderStock_serviceException_noMark() {
        // given
        List<OrderInfo> orders = Arrays.asList(cancelledOrder);
        when(orderInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(orders);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(orderItem1));
        when(inventoryFeignClient.releaseStock(any(StockOperationDTO.class)))
                .thenThrow(new RuntimeException("服务不可用"));

        // when
        compensationTask.compensateCancelledOrderStock();

        // then
        verify(inventoryFeignClient, times(1)).releaseStock(any(StockOperationDTO.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        assertFalse(cancelledOrder.getCancelReason().contains("[已补偿]"));
    }

    @Test
    @DisplayName("补偿任务 - 多个订单依次处理")
    void compensateCancelledOrderStock_multipleOrders_processedSequentially() {
        // given
        OrderInfo order2 = new OrderInfo();
        order2.setId(2L);
        order2.setOrderNo("2024010100002");
        order2.setUserId(1002L);
        order2.setStatus(OrderStatus.CANCELLED.getCode());
        order2.setCancelTime(LocalDateTime.now().minusHours(2));
        order2.setCancelReason("超时未支付");

        List<OrderInfo> orders = Arrays.asList(cancelledOrder, order2);
        when(orderInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(orders);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(orderItem1));
        when(inventoryFeignClient.releaseStock(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());
        when(orderInfoMapper.updateById(any(OrderInfo.class)))
                .thenReturn(1);

        // when
        compensationTask.compensateCancelledOrderStock();

        // then
        verify(inventoryFeignClient, times(2)).releaseStock(any(StockOperationDTO.class));
        verify(orderInfoMapper, times(2)).updateById(any(OrderInfo.class));
        assertTrue(cancelledOrder.getCancelReason().contains("[已补偿]"));
        assertTrue(order2.getCancelReason().contains("[已补偿]"));
    }

    @Test
    @DisplayName("补偿任务 - 无订单明细时直接标记补偿")
    void compensateCancelledOrderStock_noItems_marksCompensated() {
        // given
        List<OrderInfo> orders = Arrays.asList(cancelledOrder);
        when(orderInfoMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(orders);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(orderInfoMapper.updateById(any(OrderInfo.class)))
                .thenReturn(1);

        // when
        compensationTask.compensateCancelledOrderStock();

        // then
        verify(inventoryFeignClient, never()).releaseStock(any(StockOperationDTO.class));
        verify(orderInfoMapper, times(1)).updateById(any(OrderInfo.class));
        assertTrue(cancelledOrder.getCancelReason().contains("[已补偿]"));
    }
}
