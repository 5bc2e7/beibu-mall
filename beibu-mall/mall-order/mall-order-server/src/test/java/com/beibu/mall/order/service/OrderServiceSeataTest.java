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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Seata 分布式事务测试用例
 *
 * 测试目标：
 * 1. 验证分布式事务在各种异常场景下的回滚行为
 * 2. 确保数据一致性（库存、订单要么全成功，要么全失败）
 * 3. 验证幂等性检查
 *
 * 注意：这些是单元测试，使用 Mock 对象模拟外部依赖
 * 真正的集成测试需要启动 Seata Server 和所有服务
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Seata 分布式事务测试")
class OrderServiceSeataTest {

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

    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderDTO createOrderDTO;
    private Long userId;
    private SkuVO testSku;

    @BeforeEach
    void setUp() {
        userId = 1001L;

        // 准备下单请求
        createOrderDTO = new CreateOrderDTO();
        createOrderDTO.setAddressId(1L);
        createOrderDTO.setRemark("Seata 事务测试");

        CreateOrderDTO.OrderItemDTO itemDTO = new CreateOrderDTO.OrderItemDTO();
        itemDTO.setSkuId(100L);
        itemDTO.setQuantity(2);
        createOrderDTO.setItems(Collections.singletonList(itemDTO));

        // 准备商品数据
        testSku = new SkuVO();
        testSku.setId(100L);
        testSku.setSpuId(1L);
        testSku.setSpec("北部湾大虾/500g");
        testSku.setPrice(new BigDecimal("99.90"));
        testSku.setStock(10);
        testSku.setStatus(1);

        // 设置 Redis Mock
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
    }

    /**
     * 测试场景 1：库存不足时的回滚
     *
     * 预期行为：
     * 1. 查询商品成功
     * 2. 预占库存失败（库存不足）
     * 3. 抛出 BizException
     * 4. 订单不应该被创建
     * 5. 库存不应该被扣减
     */
    @Test
    @DisplayName("库存不足时 - 应该回滚，不创建订单")
    void createOrder_InsufficientStock_ShouldRollback() {
        // ========== Given ==========
        when(productFeignClient.getSkuById(100L))
                .thenReturn(Result.ok(testSku));

        // 模拟库存不足
        when(inventoryFeignClient.occupyStock(any(StockOperationDTO.class)))
                .thenReturn(Result.fail(10031, "库存不足，当前可用库存：0"));

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            orderService.createOrder(createOrderDTO, userId);
        });

        // 验证异常信息
        assertEquals(40003, exception.getCode());
        assertTrue(exception.getMessage().contains("库存不足"));

        // 验证没有保存订单（Seata 回滚）
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
        verify(orderItemMapper, never()).insert(any(OrderItem.class));

        // 验证调用了库存服务（但失败了）
        verify(inventoryFeignClient, times(1)).occupyStock(any(StockOperationDTO.class));
    }

    /**
     * 测试场景 2：订单保存失败时的回滚
     *
     * 预期行为：
     * 1. 查询商品成功
     * 2. 预占库存成功
     * 3. 保存订单失败（数据库异常）
     * 4. Seata 应该回滚整个事务
     * 5. 库存预占应该被撤销
     */
    @Test
    @DisplayName("订单保存失败时 - 应该回滚库存预占")
    void createOrder_OrderSaveFailed_ShouldRollbackInventory() {
        // ========== Given ==========
        when(productFeignClient.getSkuById(100L))
                .thenReturn(Result.ok(testSku));

        // 模拟库存预占成功
        when(inventoryFeignClient.occupyStock(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());

        // 模拟雪花算法生成ID
        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(snowflakeIdGenerator.nextOrderNo()).thenReturn("1234567890123456789");

        // 模拟订单保存失败（抛出异常）
        when(orderInfoMapper.insert(any(OrderInfo.class)))
                .thenThrow(new RuntimeException("数据库连接失败"));

        // ========== When & Then ==========
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.createOrder(createOrderDTO, userId);
        });

        assertTrue(exception.getMessage().contains("数据库连接失败"));

        // 验证调用了库存服务预占
        verify(inventoryFeignClient, times(1)).occupyStock(any(StockOperationDTO.class));

        // 验证尝试保存了订单（但失败了）
        verify(orderInfoMapper, times(1)).insert(any(OrderInfo.class));

        // 注意：在真实的 Seata 环境中，库存预占会被自动回滚
        // 这里是单元测试，无法验证 Seata 的回滚行为
        // 真正的回滚验证需要集成测试
    }

    /**
     * 测试场景 3：商品不存在时的回滚
     *
     * 预期行为：
     * 1. 查询商品失败（商品不存在）
     * 2. 抛出 BizException
     * 3. 不应该调用库存服务
     * 4. 不应该创建订单
     */
    @Test
    @DisplayName("商品不存在时 - 应该直接失败，不调用库存服务")
    void createOrder_ProductNotFound_ShouldFailWithoutInventoryCall() {
        // ========== Given ==========
        // 模拟商品不存在
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
     * 测试场景 4：商品已下架时的回滚
     *
     * 预期行为：
     * 1. 查询商品成功，但商品已下架（status = 0）
     * 2. 抛出 BizException
     * 3. 不应该调用库存服务
     * 4. 不应该创建订单
     */
    @Test
    @DisplayName("商品已下架时 - 应该直接失败，不调用库存服务")
    void createOrder_ProductOffShelf_ShouldFailWithoutInventoryCall() {
        // ========== Given ==========
        // 模拟商品已下架
        SkuVO offShelfSku = new SkuVO();
        offShelfSku.setId(100L);
        offShelfSku.setSpec("北部湾大虾/500g");
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

        // 验证没有保存订单
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    /**
     * 测试场景 5：重复提交的幂等性检查
     *
     * 预期行为：
     * 1. 第一次提交成功
     * 2. 第二次提交应该被拒绝（幂等性检查）
     * 3. 抛出 BizException，提示"请勿重复提交订单"
     */
    @Test
    @DisplayName("重复提交时 - 应该被幂等性检查拒绝")
    void createOrder_DuplicateSubmission_ShouldBeRejected() {
        // ========== Given ==========
        // 模拟 Redis 返回 false（表示 key 已存在，重复提交）
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            orderService.createOrder(createOrderDTO, userId);
        });

        assertEquals(40010, exception.getCode());
        assertTrue(exception.getMessage().contains("请勿重复提交订单"));

        // 验证没有调用商品服务
        verify(productFeignClient, never()).getSkuById(anyLong());

        // 验证没有调用库存服务
        verify(inventoryFeignClient, never()).occupyStock(any(StockOperationDTO.class));

        // 验证没有保存订单
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    /**
     * 测试场景 6：库存服务 Fallback 抛出 BizException
     *
     * 预期行为：
     * 1. 库存服务不可用，Fallback 抛出 BizException(50003)
     * 2. 应该抛出 BizException
     * 3. 不应该创建订单
     */
    @Test
    @DisplayName("库存服务 Fallback 异常时 - 应该抛出 BizException")
    void createOrder_InventoryServiceFallback_ShouldThrowBizException() {
        // ========== Given ==========
        when(productFeignClient.getSkuById(100L))
                .thenReturn(Result.ok(testSku));

        // 模拟库存服务 Fallback 抛出 BizException
        when(inventoryFeignClient.occupyStock(any(StockOperationDTO.class)))
                .thenThrow(new BizException(50003, "库存服务暂时不可用，请稍后重试"));

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            orderService.createOrder(createOrderDTO, userId);
        });

        assertEquals(50003, exception.getCode());
        assertTrue(exception.getMessage().contains("库存服务暂时不可用"));

        // 验证没有保存订单
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    /**
     * 测试场景 7：多个商品时部分库存不足
     *
     * 预期行为：
     * 1. 第一个商品预占成功
     * 2. 第二个商品预占失败（库存不足）
     * 3. 应该抛出 BizException
     * 4. 不应该创建订单
     * 5. 第一个商品的预占应该被回滚（Seata 负责）
     */
    @Test
    @DisplayName("多个商品部分库存不足时 - 应该回滚所有预占")
    void createOrder_MultipleItemsPartialInsufficient_ShouldRollbackAll() {
        // ========== Given ==========
        // 准备两个商品的下单请求
        CreateOrderDTO.OrderItemDTO item1 = new CreateOrderDTO.OrderItemDTO();
        item1.setSkuId(100L);
        item1.setQuantity(2);

        CreateOrderDTO.OrderItemDTO item2 = new CreateOrderDTO.OrderItemDTO();
        item2.setSkuId(200L);
        item2.setQuantity(5);

        createOrderDTO.setItems(java.util.Arrays.asList(item1, item2));

        // 模拟商品服务返回商品信息
        SkuVO sku1 = new SkuVO();
        sku1.setId(100L);
        sku1.setSpuId(1L);
        sku1.setSpec("北部湾大虾/500g");
        sku1.setPrice(new BigDecimal("99.90"));
        sku1.setStatus(1);

        SkuVO sku2 = new SkuVO();
        sku2.setId(200L);
        sku2.setSpuId(2L);
        sku2.setSpec("北海鱿鱼/500g");
        sku2.setPrice(new BigDecimal("79.90"));
        sku2.setStatus(1);

        when(productFeignClient.getSkuById(100L)).thenReturn(Result.ok(sku1));
        when(productFeignClient.getSkuById(200L)).thenReturn(Result.ok(sku2));

        // 第一个商品预占成功
        when(inventoryFeignClient.occupyStock(argThat(dto -> 
            dto != null && "100".equals(dto.getSkuId())
        ))).thenReturn(Result.ok());

        // 第二个商品预占失败（库存不足）
        when(inventoryFeignClient.occupyStock(argThat(dto -> 
            dto != null && "200".equals(dto.getSkuId())
        ))).thenReturn(Result.fail(10031, "库存不足"));

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            orderService.createOrder(createOrderDTO, userId);
        });

        assertEquals(40003, exception.getCode());
        assertTrue(exception.getMessage().contains("库存不足"));

        // 验证两个商品都尝试了预占
        verify(inventoryFeignClient, times(2)).occupyStock(any(StockOperationDTO.class));

        // 验证没有保存订单
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    /**
     * 测试场景 8：正常下单成功
     *
     * 预期行为：
     * 1. 查询商品成功
     * 2. 预占库存成功
     * 3. 保存订单成功
     * 4. 返回订单信息
     */
    @Test
    @DisplayName("正常下单 - 所有步骤成功")
    void createOrder_Success() {
        // ========== Given ==========
        when(productFeignClient.getSkuById(100L))
                .thenReturn(Result.ok(testSku));

        when(inventoryFeignClient.occupyStock(any(StockOperationDTO.class)))
                .thenReturn(Result.ok());

        when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
        when(snowflakeIdGenerator.nextOrderNo()).thenReturn("1234567890123456789");

        when(orderInfoMapper.insert(any(OrderInfo.class))).thenReturn(1);
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);

        // ========== When ==========
        OrderVO result = orderService.createOrder(createOrderDTO, userId);

        // ========== Then ==========
        assertNotNull(result);
        assertNotNull(result.getOrderNo());
        assertEquals(new BigDecimal("199.80"), result.getTotalAmount());
        assertEquals(0, result.getStatus());
        assertEquals("待支付", result.getStatusDesc());

        // 验证调用了商品服务
        verify(productFeignClient, times(1)).getSkuById(100L);

        // 验证调用了库存服务预占
        verify(inventoryFeignClient, times(1)).occupyStock(any(StockOperationDTO.class));

        // 验证保存了订单
        verify(orderInfoMapper, times(1)).insert(any(OrderInfo.class));
        verify(orderItemMapper, times(1)).insert(any(OrderItem.class));
    }
}
