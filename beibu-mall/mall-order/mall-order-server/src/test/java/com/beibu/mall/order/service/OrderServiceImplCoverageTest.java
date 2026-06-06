package com.beibu.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.inventory.api.dto.StockOperationDTO;
import com.beibu.mall.inventory.api.feign.InventoryFeignClient;
import com.beibu.mall.order.api.dto.OrderVO;
import com.beibu.mall.order.config.SnowflakeIdGenerator;
import com.beibu.mall.order.entity.OrderInfo;
import com.beibu.mall.order.entity.OrderItem;
import com.beibu.mall.order.mapper.OrderInfoMapper;
import com.beibu.mall.order.mapper.OrderItemMapper;
import com.beibu.mall.order.mq.OrderDelayProducer;
import com.beibu.mall.order.service.impl.OrderServiceImpl;
import com.beibu.mall.product.api.feign.ProductFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderServiceImpl 补充覆盖测试
 *
 * 目标：覆盖现有测试未覆盖的公共方法，提升行覆盖率至 ≥80%
 * 覆盖方法：listMyOrders、getOrderDetail、cancelOrder、handlePaymentSuccess、cancelTimeoutOrder
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl 补充覆盖测试")
class OrderServiceImplCoverageTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductFeignClient productFeignClient;

    @Mock
    private InventoryFeignClient inventoryFeignClient;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private OrderDelayProducer orderDelayProducer;

    @Mock
    private OrderTransactionService orderTransactionService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = 1001L;

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        // TransactionTemplate 直接执行回调（模拟事务行为）
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        // OrderTransactionService 默认返回值（handlePaymentSuccess、cancelTimeoutOrder 使用）
        lenient().when(orderTransactionService.updateOrderToPaid(anyString(), any(LocalDateTime.class)))
                .thenReturn(createOrderInfo(1L, "ORDER_001", 0));
        lenient().when(orderTransactionService.getOrderItems(anyString()))
                .thenReturn(Collections.emptyList());
        lenient().when(orderTransactionService.updateOrderToCancelled(anyString(), anyString()))
                .thenReturn(createOrderInfo(1L, "ORDER_001", 0));
    }

    // ========== listMyOrders 测试 ==========

    @Test
    @DisplayName("listMyOrders - 正常分页查询，返回订单列表")
    void listMyOrders_NormalPagination() {
        // Given
        OrderInfo order1 = createOrderInfo(1L, "ORDER_001", 0);
        Page<OrderInfo> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(Collections.singletonList(order1));

        when(orderInfoMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        OrderItem item = new OrderItem();
        item.setOrderId(1L);
        item.setSkuId(100L);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("99.90"));
        item.setSubtotal(new BigDecimal("199.80"));
        item.setProductName("测试商品");

        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(item));

        // When
        Page<OrderVO> result = orderService.listMyOrders(userId, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals("ORDER_001", result.getRecords().get(0).getOrderNo());
        assertEquals(1, result.getRecords().get(0).getItems().size());
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("listMyOrders - 用户无订单，返回空列表")
    void listMyOrders_EmptyResult() {
        // Given
        Page<OrderInfo> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(Collections.emptyList());

        when(orderInfoMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage);

        // When
        Page<OrderVO> result = orderService.listMyOrders(userId, 1, 10);

        // Then
        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("listMyOrders - 多页数据，查询第2页")
    void listMyOrders_MultiplePages() {
        // Given
        OrderInfo order2 = createOrderInfo(2L, "ORDER_002", 1);
        Page<OrderInfo> pageResult = new Page<>(2, 1, 2);
        pageResult.setRecords(Collections.singletonList(order2));

        when(orderInfoMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        OrderItem item = new OrderItem();
        item.setOrderId(2L);
        item.setSkuId(200L);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("59.90"));
        item.setSubtotal(new BigDecimal("59.90"));
        item.setProductName("测试商品2");

        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(item));

        // When
        Page<OrderVO> result = orderService.listMyOrders(userId, 2, 1);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals("ORDER_002", result.getRecords().get(0).getOrderNo());
        assertEquals(2, result.getTotal());
    }

    // ========== getOrderDetail 测试 ==========

    @Test
    @DisplayName("getOrderDetail - 正常查询成功")
    void getOrderDetail_Success() {
        // Given
        OrderInfo orderInfo = createOrderInfo(1L, "ORDER_001", 0);
        when(orderInfoMapper.selectById(1L)).thenReturn(orderInfo);

        OrderItem item = new OrderItem();
        item.setOrderId(1L);
        item.setSkuId(100L);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("99.90"));
        item.setSubtotal(new BigDecimal("199.80"));
        item.setProductName("测试商品");

        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(item));

        // When
        OrderVO result = orderService.getOrderDetail(1L, userId);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("ORDER_001", result.getOrderNo());
        assertEquals(0, result.getStatus());
        assertEquals("待支付", result.getStatusDesc());
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());
    }

    @Test
    @DisplayName("getOrderDetail - 订单不存在，抛出 BizException 40004")
    void getOrderDetail_OrderNotFound() {
        // Given
        when(orderInfoMapper.selectById(999L)).thenReturn(null);

        // When & Then
        BizException exception = assertThrows(BizException.class, () ->
                orderService.getOrderDetail(999L, userId));
        assertEquals(40004, exception.getCode());
        assertTrue(exception.getMessage().contains("订单不存在"));
    }

    @Test
    @DisplayName("getOrderDetail - 无权访问他人订单，抛出 BizException 40005")
    void getOrderDetail_UnauthorizedAccess() {
        // Given
        OrderInfo orderInfo = createOrderInfo(1L, "ORDER_001", 0);
        orderInfo.setUserId(9999L);  // 不同用户
        when(orderInfoMapper.selectById(1L)).thenReturn(orderInfo);

        // When & Then
        BizException exception = assertThrows(BizException.class, () ->
                orderService.getOrderDetail(1L, userId));
        assertEquals(40005, exception.getCode());
        assertTrue(exception.getMessage().contains("无权访问"));
    }

    // ========== cancelOrder 测试 ==========

    @Test
    @DisplayName("cancelOrder - 订单不存在，抛出 BizException 40004")
    void cancelOrder_OrderNotFound() {
        // Given
        when(orderInfoMapper.selectById(999L)).thenReturn(null);

        // When & Then
        BizException exception = assertThrows(BizException.class, () ->
                orderService.cancelOrder(999L, userId, "测试"));
        assertEquals(40004, exception.getCode());
    }

    @Test
    @DisplayName("cancelOrder - 无权操作他人订单，抛出 BizException 40005")
    void cancelOrder_UnauthorizedUser() {
        // Given
        OrderInfo orderInfo = createOrderInfo(1L, "ORDER_001", 0);
        orderInfo.setUserId(9999L);
        when(orderInfoMapper.selectById(1L)).thenReturn(orderInfo);

        // When & Then
        BizException exception = assertThrows(BizException.class, () ->
                orderService.cancelOrder(1L, userId, "测试"));
        assertEquals(40005, exception.getCode());
        assertTrue(exception.getMessage().contains("无权操作"));
    }

    @Test
    @DisplayName("cancelOrder - 订单状态为 null，抛出 BizException 40008")
    void cancelOrder_StatusNull() {
        // Given
        OrderInfo orderInfo = createOrderInfo(1L, "ORDER_001", 0);
        orderInfo.setStatus(null);
        when(orderInfoMapper.selectById(1L)).thenReturn(orderInfo);

        // When & Then
        BizException exception = assertThrows(BizException.class, () ->
                orderService.cancelOrder(1L, userId, "测试"));
        assertEquals(40008, exception.getCode());
        assertTrue(exception.getMessage().contains("状态异常"));
    }

    @Test
    @DisplayName("cancelOrder - 库存释放成功，订单标记已补偿")
    void cancelOrder_Success() {
        // Given
        OrderInfo orderInfo = createOrderInfo(1L, "ORDER_001", 0);
        when(orderInfoMapper.selectById(1L)).thenReturn(orderInfo);
        when(orderInfoMapper.updateById(any(OrderInfo.class))).thenReturn(1);

        OrderItem item = new OrderItem();
        item.setSkuId(100L);
        item.setQuantity(2);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(item));

        when(inventoryFeignClient.releaseStock(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());

        // When
        orderService.cancelOrder(1L, userId, "不想买了");

        // Then
        verify(inventoryFeignClient, times(1)).releaseStock(any(StockOperationDTO.class));
        // 第1次：设置取消状态；第2次：标记[已补偿]
        verify(orderInfoMapper, times(2)).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("cancelOrder - 库存释放部分失败，不标记已补偿")
    void cancelOrder_StockReleasePartialFail() {
        // Given
        OrderInfo orderInfo = createOrderInfo(1L, "ORDER_001", 0);
        when(orderInfoMapper.selectById(1L)).thenReturn(orderInfo);
        when(orderInfoMapper.updateById(any(OrderInfo.class))).thenReturn(1);

        OrderItem item1 = new OrderItem();
        item1.setSkuId(100L);
        item1.setQuantity(1);
        OrderItem item2 = new OrderItem();
        item2.setSkuId(200L);
        item2.setQuantity(1);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(item1, item2));

        // 第1个释放成功，第2个失败
        when(inventoryFeignClient.releaseStock(argThat(dto ->
                dto != null && "100".equals(dto.getSkuId())
        ))).thenReturn(Result.ok());
        when(inventoryFeignClient.releaseStock(argThat(dto ->
                dto != null && "200".equals(dto.getSkuId())
        ))).thenReturn(Result.fail(500, "库存服务异常"));

        // When
        orderService.cancelOrder(1L, userId, "不想买了");

        // Then
        verify(inventoryFeignClient, times(2)).releaseStock(any(StockOperationDTO.class));
        // 只更新1次（设置取消状态），不标记[已补偿]
        verify(orderInfoMapper, times(1)).updateById(any(OrderInfo.class));
    }

    // ========== handlePaymentSuccess 测试 ==========

    @Test
    @DisplayName("handlePaymentSuccess - 正常成功流程")
    void handlePaymentSuccess_Success() {
        // Given
        String orderNo = "ORDER_001";
        LocalDateTime paymentTime = LocalDateTime.now();

        OrderItem item = new OrderItem();
        item.setSkuId(100L);
        item.setQuantity(2);
        when(orderTransactionService.getOrderItems(orderNo))
                .thenReturn(Collections.singletonList(item));
        when(inventoryFeignClient.confirmDeduct(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());

        // When
        orderService.handlePaymentSuccess(orderNo, 1L, new BigDecimal("199.80"), paymentTime);

        // Then
        verify(orderTransactionService, times(1)).updateOrderToPaid(orderNo, paymentTime);
        verify(orderTransactionService, times(1)).getOrderItems(orderNo);
        verify(inventoryFeignClient, times(1)).confirmDeduct(any(StockOperationDTO.class));
    }

    @Test
    @DisplayName("handlePaymentSuccess - 库存确认扣减部分失败")
    void handlePaymentSuccess_StockConfirmPartialFail() {
        // Given
        String orderNo = "ORDER_001";
        LocalDateTime paymentTime = LocalDateTime.now();

        OrderItem item1 = new OrderItem();
        item1.setSkuId(100L);
        item1.setQuantity(1);
        OrderItem item2 = new OrderItem();
        item2.setSkuId(200L);
        item2.setQuantity(1);
        when(orderTransactionService.getOrderItems(orderNo))
                .thenReturn(Arrays.asList(item1, item2));

        // 第1个确认成功，第2个失败
        when(inventoryFeignClient.confirmDeduct(argThat(dto ->
                dto != null && "100".equals(dto.getSkuId())
        ))).thenReturn(Result.ok());
        when(inventoryFeignClient.confirmDeduct(argThat(dto ->
                dto != null && "200".equals(dto.getSkuId())
        ))).thenReturn(Result.fail(500, "库存服务异常"));

        // When
        orderService.handlePaymentSuccess(orderNo, 1L, new BigDecimal("199.80"), paymentTime);

        // Then
        verify(orderTransactionService, times(1)).updateOrderToPaid(orderNo, paymentTime);
        verify(inventoryFeignClient, times(2)).confirmDeduct(any(StockOperationDTO.class));
    }

    // ========== cancelTimeoutOrder 测试 ==========

    @Test
    @DisplayName("cancelTimeoutOrder - 正常超时取消流程")
    void cancelTimeoutOrder_Success() {
        // Given
        String orderNo = "ORDER_001";
        OrderInfo orderInfo = createOrderInfo(1L, orderNo, 0);
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(orderInfo);

        OrderItem item = new OrderItem();
        item.setSkuId(100L);
        item.setQuantity(2);
        when(orderTransactionService.getOrderItems(orderNo))
                .thenReturn(Collections.singletonList(item));
        when(inventoryFeignClient.releaseStock(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());

        // When
        orderService.cancelTimeoutOrder(orderNo);

        // Then
        verify(orderTransactionService, times(1)).updateOrderToCancelled(orderNo, "超时未支付自动取消");
        verify(orderTransactionService, times(1)).getOrderItems(orderNo);
        verify(inventoryFeignClient, times(1)).releaseStock(any(StockOperationDTO.class));
    }

    @Test
    @DisplayName("cancelTimeoutOrder - 订单不存在，日志并返回")
    void cancelTimeoutOrder_OrderNotFound() {
        // Given
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        // When
        orderService.cancelTimeoutOrder("NOT_EXIST");

        // Then
        verify(orderTransactionService, never()).updateOrderToCancelled(anyString(), anyString());
    }

    @Test
    @DisplayName("cancelTimeoutOrder - 订单已支付，跳过取消")
    void cancelTimeoutOrder_AlreadyPaid() {
        // Given
        String orderNo = "ORDER_001";
        OrderInfo orderInfo = createOrderInfo(1L, orderNo, 1);  // 已支付
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(orderInfo);

        // When
        orderService.cancelTimeoutOrder(orderNo);

        // Then
        verify(orderTransactionService, never()).updateOrderToCancelled(anyString(), anyString());
        verify(orderTransactionService, never()).getOrderItems(anyString());
    }

    @Test
    @DisplayName("cancelTimeoutOrder - 库存释放失败，日志记录但不抛异常")
    void cancelTimeoutOrder_StockReleaseFail() {
        // Given
        String orderNo = "ORDER_001";
        OrderInfo orderInfo = createOrderInfo(1L, orderNo, 0);
        when(orderInfoMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(orderInfo);

        OrderItem item = new OrderItem();
        item.setSkuId(100L);
        item.setQuantity(2);
        when(orderTransactionService.getOrderItems(orderNo))
                .thenReturn(Collections.singletonList(item));
        when(inventoryFeignClient.releaseStock(any(StockOperationDTO.class)))
                .thenReturn(Result.fail(500, "库存服务异常"));

        // When（不应抛异常）
        assertDoesNotThrow(() -> orderService.cancelTimeoutOrder(orderNo));

        // Then
        verify(orderTransactionService, times(1)).updateOrderToCancelled(orderNo, "超时未支付自动取消");
        verify(inventoryFeignClient, times(1)).releaseStock(any(StockOperationDTO.class));
    }

    // ========== 工具方法 ==========

    private OrderInfo createOrderInfo(Long id, String orderNo, Integer status) {
        OrderInfo info = new OrderInfo();
        info.setId(id);
        info.setOrderNo(orderNo);
        info.setUserId(userId);
        info.setStatus(status);
        info.setTotalAmount(new BigDecimal("199.80"));
        info.setPayAmount(new BigDecimal("199.80"));
        info.setFreightAmount(BigDecimal.ZERO);
        info.setDiscountAmount(BigDecimal.ZERO);
        info.setReceiverName("测试用户");
        info.setReceiverPhone("13800138000");
        info.setReceiverDetail("测试地址");
        info.setCreateTime(LocalDateTime.now());
        return info;
    }
}
