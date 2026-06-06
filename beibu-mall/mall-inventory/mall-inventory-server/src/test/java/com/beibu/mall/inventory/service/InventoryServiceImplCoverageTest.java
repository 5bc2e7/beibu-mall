package com.beibu.mall.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.inventory.entity.InventoryItem;
import com.beibu.mall.inventory.entity.InventoryLog;
import com.beibu.mall.inventory.mapper.InventoryItemMapper;
import com.beibu.mall.inventory.mapper.InventoryLogMapper;
import com.beibu.mall.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * InventoryServiceImpl 单元测试（Mockito）
 *
 * 覆盖集成测试未覆盖的边界场景：
 * - 各操作 mapper 返回 0 行（库存/预占不足）
 * - 负数数量校验
 * - orderId 为 null 的幂等处理
 * - saveInventoryLog 流水记录验证
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceImplCoverageTest {

    private static final String TEST_SKU = "SKU_001";
    private static final String TEST_ORDER = "ORDER_001";

    @Mock
    private InventoryItemMapper inventoryItemMapper;

    @Mock
    private InventoryLogMapper inventoryLogMapper;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private InventoryItem testItem;

    @BeforeEach
    void setUp() {
        testItem = new InventoryItem();
        testItem.setSkuId(TEST_SKU);
        testItem.setProductId("P_001");
        testItem.setProductName("测试商品");
        testItem.setAvailableStock(10);
        testItem.setLockedStock(3);
        testItem.setTotalStock(10);
        testItem.setVersion(0);
    }

    // ==================== occupyStock 边界场景 ====================

    @Test
    @DisplayName("预占失败：库存不足时抛 BizException(10031)")
    void occupyStock_insufficientStock() {
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(inventoryItemMapper.occupyStock(TEST_SKU, 5)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.occupyStock(TEST_SKU, 5, TEST_ORDER));

        assertEquals(10031, ex.getCode());
        verify(inventoryItemMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(inventoryLogMapper, never()).insert(any(InventoryLog.class));
    }

    @Test
    @DisplayName("预占校验：负数数量抛 BizException(10034)")
    void occupyStock_negativeQuantity() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.occupyStock(TEST_SKU, -1, TEST_ORDER));

        assertEquals(10034, ex.getCode());
        verify(inventoryItemMapper, never()).occupyStock(anyString(), anyInt());
    }

    // ==================== releaseStock 边界场景 ====================

    @Test
    @DisplayName("释放失败：预占库存不足时抛 BizException(10032)")
    void releaseStock_insufficientLockedStock() {
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(inventoryItemMapper.releaseStock(TEST_SKU, 5)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.releaseStock(TEST_SKU, 5, TEST_ORDER));

        assertEquals(10032, ex.getCode());
        verify(inventoryItemMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(inventoryLogMapper, never()).insert(any(InventoryLog.class));
    }

    @Test
    @DisplayName("释放校验：负数数量抛 BizException(10034)")
    void releaseStock_negativeQuantity() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.releaseStock(TEST_SKU, -1, TEST_ORDER));

        assertEquals(10034, ex.getCode());
        verify(inventoryItemMapper, never()).releaseStock(anyString(), anyInt());
    }

    // ==================== confirmDeduct 边界场景 ====================

    @Test
    @DisplayName("确认扣减失败：预占库存不足时抛 BizException(10033)")
    void confirmDeduct_insufficientLockedStock() {
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(inventoryItemMapper.confirmDeduct(TEST_SKU, 5)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.confirmDeduct(TEST_SKU, 5, TEST_ORDER));

        assertEquals(10033, ex.getCode());
        verify(inventoryItemMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(inventoryLogMapper, never()).insert(any(InventoryLog.class));
    }

    @Test
    @DisplayName("确认扣减校验：负数数量抛 BizException(10034)")
    void confirmDeduct_negativeQuantity() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.confirmDeduct(TEST_SKU, -1, TEST_ORDER));

        assertEquals(10034, ex.getCode());
        verify(inventoryItemMapper, never()).confirmDeduct(anyString(), anyInt());
    }

    // ==================== deductStock 边界场景 ====================

    @Test
    @DisplayName("扣减失败：库存不足时抛 BizException(10031)")
    void deductStock_insufficientStock() {
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(inventoryItemMapper.deductStock(TEST_SKU, 5)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.deductStock(TEST_SKU, 5, TEST_ORDER));

        assertEquals(10031, ex.getCode());
        verify(inventoryItemMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(inventoryLogMapper, never()).insert(any(InventoryLog.class));
    }

    @Test
    @DisplayName("扣减校验：负数数量抛 BizException(10034)")
    void deductStock_negativeQuantity() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.deductStock(TEST_SKU, -1, TEST_ORDER));

        assertEquals(10034, ex.getCode());
        verify(inventoryItemMapper, never()).deductStock(anyString(), anyInt());
    }

    // ==================== orderId 为 null 幂等跳过 ====================

    @Test
    @DisplayName("幂等：orderId 为 null 时跳过幂等检查，正常执行扣减")
    void deductStock_nullOrderId_skipsIdempotentCheck() {
        when(inventoryItemMapper.deductStock(TEST_SKU, 1)).thenReturn(1);
        when(inventoryItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testItem);

        inventoryService.deductStock(TEST_SKU, 1, null);

        // orderId=null 时不应查询流水表做幂等检查
        verify(inventoryLogMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(inventoryItemMapper).deductStock(TEST_SKU, 1);
        verify(inventoryLogMapper).insert(any(InventoryLog.class));
    }

    // ==================== 流水记录验证 ====================

    @Test
    @DisplayName("扣减成功后正确记录库存流水")
    void deductStock_savesInventoryLog() {
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(inventoryItemMapper.deductStock(TEST_SKU, 2)).thenReturn(1);
        when(inventoryItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testItem);

        inventoryService.deductStock(TEST_SKU, 2, TEST_ORDER);

        ArgumentCaptor<InventoryLog> logCaptor = ArgumentCaptor.forClass(InventoryLog.class);
        verify(inventoryLogMapper).insert(logCaptor.capture());

        InventoryLog capturedLog = logCaptor.getValue();
        assertEquals(TEST_SKU, capturedLog.getSkuId());
        assertEquals(TEST_ORDER, capturedLog.getOrderId());
        assertEquals("DEDUCT", capturedLog.getChangeType());
        assertEquals(2, capturedLog.getChangeQuantity());
        assertEquals("直接扣减库存", capturedLog.getRemark());
    }

    @Test
    @DisplayName("预占成功后正确记录库存流水")
    void occupyStock_savesInventoryLog() {
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(inventoryItemMapper.occupyStock(TEST_SKU, 3)).thenReturn(1);
        when(inventoryItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testItem);

        inventoryService.occupyStock(TEST_SKU, 3, TEST_ORDER);

        ArgumentCaptor<InventoryLog> logCaptor = ArgumentCaptor.forClass(InventoryLog.class);
        verify(inventoryLogMapper).insert(logCaptor.capture());

        InventoryLog capturedLog = logCaptor.getValue();
        assertEquals(TEST_SKU, capturedLog.getSkuId());
        assertEquals(TEST_ORDER, capturedLog.getOrderId());
        assertEquals("OCCUPY", capturedLog.getChangeType());
        assertEquals(3, capturedLog.getChangeQuantity());
        assertEquals("预占库存", capturedLog.getRemark());
    }

    @Test
    @DisplayName("释放成功后正确记录库存流水")
    void releaseStock_savesInventoryLog() {
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(inventoryItemMapper.releaseStock(TEST_SKU, 2)).thenReturn(1);
        when(inventoryItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testItem);

        inventoryService.releaseStock(TEST_SKU, 2, TEST_ORDER);

        ArgumentCaptor<InventoryLog> logCaptor = ArgumentCaptor.forClass(InventoryLog.class);
        verify(inventoryLogMapper).insert(logCaptor.capture());

        InventoryLog capturedLog = logCaptor.getValue();
        assertEquals(TEST_SKU, capturedLog.getSkuId());
        assertEquals(TEST_ORDER, capturedLog.getOrderId());
        assertEquals("RELEASE", capturedLog.getChangeType());
        assertEquals(-2, capturedLog.getChangeQuantity());
        assertEquals("释放预占库存", capturedLog.getRemark());
    }

    @Test
    @DisplayName("确认扣减成功后正确记录库存流水")
    void confirmDeduct_savesInventoryLog() {
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(inventoryItemMapper.confirmDeduct(TEST_SKU, 3)).thenReturn(1);
        when(inventoryItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testItem);

        inventoryService.confirmDeduct(TEST_SKU, 3, TEST_ORDER);

        ArgumentCaptor<InventoryLog> logCaptor = ArgumentCaptor.forClass(InventoryLog.class);
        verify(inventoryLogMapper).insert(logCaptor.capture());

        InventoryLog capturedLog = logCaptor.getValue();
        assertEquals(TEST_SKU, capturedLog.getSkuId());
        assertEquals(TEST_ORDER, capturedLog.getOrderId());
        assertEquals("CONFIRM", capturedLog.getChangeType());
        assertEquals(3, capturedLog.getChangeQuantity());
        assertEquals("确认扣减库存", capturedLog.getRemark());
    }

    // ==================== 幂等跳过（Mockito 验证） ====================

    @Test
    @DisplayName("幂等：已处理的 orderId 重复扣减直接跳过，不调用 mapper")
    void deductStock_idempotent_skipsMapperCall() {
        InventoryLog existingLog = new InventoryLog();
        existingLog.setOrderId(TEST_ORDER);
        existingLog.setChangeType("DEDUCT");
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingLog);

        inventoryService.deductStock(TEST_SKU, 1, TEST_ORDER);

        verify(inventoryItemMapper, never()).deductStock(anyString(), anyInt());
        verify(inventoryLogMapper, never()).insert(any(InventoryLog.class));
    }

    @Test
    @DisplayName("幂等：已处理的 orderId 重复预占直接跳过")
    void occupyStock_idempotent_skipsMapperCall() {
        InventoryLog existingLog = new InventoryLog();
        existingLog.setOrderId(TEST_ORDER);
        existingLog.setChangeType("OCCUPY");
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingLog);

        inventoryService.occupyStock(TEST_SKU, 3, TEST_ORDER);

        verify(inventoryItemMapper, never()).occupyStock(anyString(), anyInt());
        verify(inventoryLogMapper, never()).insert(any(InventoryLog.class));
    }

    @Test
    @DisplayName("幂等：已处理的 orderId 重复释放直接跳过")
    void releaseStock_idempotent_skipsMapperCall() {
        InventoryLog existingLog = new InventoryLog();
        existingLog.setOrderId(TEST_ORDER);
        existingLog.setChangeType("RELEASE");
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingLog);

        inventoryService.releaseStock(TEST_SKU, 2, TEST_ORDER);

        verify(inventoryItemMapper, never()).releaseStock(anyString(), anyInt());
        verify(inventoryLogMapper, never()).insert(any(InventoryLog.class));
    }

    @Test
    @DisplayName("幂等：已处理的 orderId 重复确认扣减直接跳过")
    void confirmDeduct_idempotent_skipsMapperCall() {
        InventoryLog existingLog = new InventoryLog();
        existingLog.setOrderId(TEST_ORDER);
        existingLog.setChangeType("CONFIRM");
        when(inventoryLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingLog);

        inventoryService.confirmDeduct(TEST_SKU, 3, TEST_ORDER);

        verify(inventoryItemMapper, never()).confirmDeduct(anyString(), anyInt());
        verify(inventoryLogMapper, never()).insert(any(InventoryLog.class));
    }

    // Helper to avoid anyString() warnings
    private static String anyString() {
        return any(String.class);
    }

    private static int anyInt() {
        return any(int.class);
    }
}
