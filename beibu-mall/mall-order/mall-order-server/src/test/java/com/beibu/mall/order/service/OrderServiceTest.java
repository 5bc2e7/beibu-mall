package com.beibu.mall.order.service;

import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.inventory.api.dto.StockOperationDTO;
import com.beibu.mall.inventory.api.feign.InventoryFeignClient;
import com.beibu.mall.order.api.dto.CreateOrderDTO;
import com.beibu.mall.order.api.dto.OrderVO;
import com.beibu.mall.order.config.SnowflakeIdGenerator;
import com.beibu.mall.order.entity.OrderInfo;
import com.beibu.mall.order.entity.OrderItem;
import com.beibu.mall.order.mapper.OrderInfoMapper;
import com.beibu.mall.order.mapper.OrderItemMapper;
import com.beibu.mall.order.service.impl.OrderServiceImpl;
import com.beibu.mall.product.api.dto.SkuVO;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 订单服务单元测试
 *
 * 什么是单元测试？
 * 单元测试是对程序中最小的可测试单元（通常是方法）进行验证。
 * 目的：确保代码逻辑正确，修改代码后不会破坏已有功能。
 *
 * 为什么用 Mockito？
 * 订单服务依赖商品服务、库存服务等外部服务。
 * 单元测试时不应该真的调用这些服务（太慢、不稳定）。
 * Mockito 可以创建"假的"依赖对象，模拟它们的行为。
 *
 * @ExtendWith(MockitoExtension.class)：启用 Mockito 框架
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    /**
     * @Mock：创建一个假的 Mapper 对象
     * 这些假对象不会真的访问数据库，而是根据我们的设置返回预设的结果
     */
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

    /**
     * @InjectMocks：创建真实的 Service 对象，并把上面的 Mock 对象注入进去
     * 这样 Service 调用 Mapper 时，实际上调用的是 Mock 对象
     */
    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderDTO createOrderDTO;
    private Long userId;
    private SkuVO testSku;

    /**
     * @BeforeEach：每个测试方法执行前都会调用这个方法
     * 作用：准备测试数据，避免重复代码
     */
    @BeforeEach
    void setUp() {
        userId = 1001L;

        // 准备下单请求
        createOrderDTO = new CreateOrderDTO();
        createOrderDTO.setAddressId(1L);
        createOrderDTO.setRemark("测试订单");

        CreateOrderDTO.OrderItemDTO itemDTO = new CreateOrderDTO.OrderItemDTO();
        itemDTO.setSkuId(100L);
        itemDTO.setQuantity(2);
        createOrderDTO.setItems(Collections.singletonList(itemDTO));

        // 准备商品数据
        testSku = new SkuVO();
        testSku.setId(100L);
        testSku.setSpuId(1L);
        testSku.setSpec("鲜活大虾/500g");
        testSku.setPrice(new BigDecimal("99.90"));
        testSku.setStock(10);
        testSku.setStatus(1);

        // 设置 Redis Mock
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        // 设置 TransactionTemplate Mock：直接执行回调（模拟事务行为）
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
    }

    /**
     * 测试正常下单流程
     *
     * 场景：商品存在、库存充足，下单应该成功
     *
     * @DisplayName：测试方法的显示名称，让测试报告更易读
     */
    @Test
    @DisplayName("正常下单 - 商品存在且库存充足")
    void createOrder_Success() {
        // ========== Given：准备测试数据 ==========

        // 模拟商品服务返回商品信息
        when(productFeignClient.getSkuById(100L))
                .thenReturn(Result.ok(testSku));

        // 模拟库存服务预占库存成功
        when(inventoryFeignClient.occupyStock(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());

        // 模拟雪花算法生成ID
        when(snowflakeIdGenerator.nextId())
                .thenReturn(123456789L);
        when(snowflakeIdGenerator.nextOrderNo())
                .thenReturn("1234567890123456789");

        // 模拟数据库插入成功
        when(orderInfoMapper.insert(any(OrderInfo.class)))
                .thenReturn(1);
        when(orderItemMapper.insert(any(OrderItem.class)))
                .thenReturn(1);

        // ========== When：执行被测试的方法 ==========
        OrderVO result = orderService.createOrder(createOrderDTO, userId);

        // ========== Then：验证结果 ==========

        // 验证返回结果不为空
        assertNotNull(result);
        assertNotNull(result.getOrderNo());

        // 验证金额计算正确
        assertEquals(new BigDecimal("199.80"), result.getTotalAmount());

        // 验证订单状态是"待支付"
        assertEquals(0, result.getStatus());
        assertEquals("待支付", result.getStatusDesc());

        // 验证商品列表
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());

        // 验证调用了商品服务
        verify(productFeignClient, times(1)).getSkuById(100L);

        // 验证调用了库存服务预占库存
        verify(inventoryFeignClient, times(1)).occupyStock(any(StockOperationDTO.class));

        // 验证保存了订单主表
        verify(orderInfoMapper, times(1)).insert(any(OrderInfo.class));

        // 验证保存了订单明细
        verify(orderItemMapper, times(1)).insert(any(OrderItem.class));
    }

    /**
     * 测试库存不足时下单失败
     *
     * 场景：库存服务返回库存不足，下单应该失败，且不生成订单
     *
     * 重要：这个测试确保了"库存不足"时不会生成空订单！
     */
    @Test
    @DisplayName("库存不足时下单失败 - 不生成订单")
    void createOrder_InsufficientStock_ShouldFail() {
        // ========== Given ==========

        // 模拟商品服务返回商品信息
        when(productFeignClient.getSkuById(100L))
                .thenReturn(Result.ok(testSku));

        // 模拟库存服务返回库存不足
        when(inventoryFeignClient.occupyStock(any(StockOperationDTO.class)))
                .thenReturn(Result.fail(10031, "库存不足，当前可用库存：0"));

        // ========== When & Then ==========

        // 执行下单，期望抛出 BizException
        BizException exception = assertThrows(BizException.class, () -> {
            orderService.createOrder(createOrderDTO, userId);
        });

        // 验证异常信息
        assertEquals(40003, exception.getCode());
        assertTrue(exception.getMessage().contains("库存不足"));

        // 验证没有保存订单（因为异常会导致事务回滚）
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
        verify(orderItemMapper, never()).insert(any(OrderItem.class));
    }

    /**
     * 测试商品不存在时下单失败
     */
    @Test
    @DisplayName("商品不存在时下单失败")
    void createOrder_ProductNotFound_ShouldFail() {
        // ========== Given ==========

        // 模拟商品服务返回商品不存在
        when(productFeignClient.getSkuById(100L))
                .thenReturn(Result.fail(404, "商品不存在"));

        // ========== When & Then ==========

        BizException exception = assertThrows(BizException.class, () -> {
            orderService.createOrder(createOrderDTO, userId);
        });

        assertEquals(40001, exception.getCode());
        assertTrue(exception.getMessage().contains("商品不存在"));

        // 验证没有调用库存服务
        verify(inventoryFeignClient, never()).occupyStock(any(StockOperationDTO.class));

        // 验证没有保存订单
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    /**
     * 测试商品已下架时下单失败
     */
    @Test
    @DisplayName("商品已下架时下单失败")
    void createOrder_ProductOffShelf_ShouldFail() {
        // ========== Given ==========

        // 模拟商品已下架（status = 0）
        SkuVO offShelfSku = new SkuVO();
        offShelfSku.setId(100L);
        offShelfSku.setSpec("鲜活大虾/500g");
        offShelfSku.setPrice(new BigDecimal("99.90"));
        offShelfSku.setStatus(0);  // 下架状态

        when(productFeignClient.getSkuById(100L))
                .thenReturn(Result.ok(offShelfSku));

        // ========== When & Then ==========

        BizException exception = assertThrows(BizException.class, () -> {
            orderService.createOrder(createOrderDTO, userId);
        });

        assertEquals(40002, exception.getCode());
        assertTrue(exception.getMessage().contains("已下架"));

        // 验证没有调用库存服务
        verify(inventoryFeignClient, never()).occupyStock(any(StockOperationDTO.class));
    }

    /**
     * 测试取消订单成功
     */
    @Test
    @DisplayName("取消订单成功 - 释放预占库存")
    void cancelOrder_Success() {
        // ========== Given ==========

        // 模拟查询订单
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(123456789L);
        orderInfo.setOrderNo("1234567890123456789");
        orderInfo.setUserId(userId);
        orderInfo.setStatus(0);  // 待支付状态

        when(orderInfoMapper.selectById(123456789L))
                .thenReturn(orderInfo);

        // 模拟查询订单明细
        OrderItem orderItem = new OrderItem();
        orderItem.setSkuId(100L);
        orderItem.setQuantity(2);

        when(orderItemMapper.selectList(any()))
                .thenReturn(Collections.singletonList(orderItem));

        // 模拟库存服务释放库存成功
        when(inventoryFeignClient.releaseStock(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());

        // 模拟更新订单成功
        when(orderInfoMapper.updateById(any(OrderInfo.class)))
                .thenReturn(1);

        // ========== When ==========

        orderService.cancelOrder(123456789L, userId, "不想买了");

        // ========== Then ==========

        // 验证调用了库存服务释放库存
        verify(inventoryFeignClient, times(1)).releaseStock(any(StockOperationDTO.class));

        // 验证更新了订单状态（第1次：设置取消状态；第2次：标记[已补偿]）
        verify(orderInfoMapper, times(2)).updateById(any(OrderInfo.class));
    }

    /**
     * 测试取消非待支付订单失败
     */
    @Test
    @DisplayName("取消非待支付订单失败")
    void cancelOrder_NotPendingStatus_ShouldFail() {
        // ========== Given ==========

        // 模拟订单状态为"已支付"
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(123456789L);
        orderInfo.setUserId(userId);
        orderInfo.setStatus(1);  // 已支付

        when(orderInfoMapper.selectById(123456789L))
                .thenReturn(orderInfo);

        // ========== When & Then ==========

        BizException exception = assertThrows(BizException.class, () -> {
            orderService.cancelOrder(123456789L, userId, null);
        });

        assertEquals(40006, exception.getCode());
        assertTrue(exception.getMessage().contains("不允许取消"));
    }
}
