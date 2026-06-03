package com.beibu.mall.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.inventory.entity.InventoryItem;
import com.beibu.mall.inventory.entity.InventoryLog;
import com.beibu.mall.inventory.mapper.InventoryItemMapper;
import com.beibu.mall.inventory.mapper.InventoryLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 库存服务集成测试
 *
 * 为什么不用 @Transactional？
 * 因为并发测试的 50 个线程跑在不同线程里，
 * Spring 的事务是基于 ThreadLocal 的（每个线程有自己的事务），
 * 主线程的 @Transactional 事务对子线程不可见。
 * 所以我们必须真正写入数据库，测试完再手动清理。
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceIntegrationTest {

    private static final String TEST_SKU_ID = "TEST_SKU_001";

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryItemMapper inventoryItemMapper;

    @Autowired
    private InventoryLogMapper inventoryLogMapper;

    @BeforeEach
    void setUp() {
        // 先清理可能残留的测试数据（上一次测试失败时不会回滚）
        inventoryItemMapper.delete(
                new LambdaQueryWrapper<InventoryItem>().eq(InventoryItem::getSkuId, TEST_SKU_ID)
        );
        inventoryLogMapper.delete(
                new LambdaQueryWrapper<InventoryLog>().eq(InventoryLog::getSkuId, TEST_SKU_ID)
        );

        // 插入测试商品：库存 10 个
        InventoryItem item = new InventoryItem();
        item.setSkuId(TEST_SKU_ID);
        item.setProductId("P_TEST");
        item.setProductName("集成测试商品");
        item.setAvailableStock(10);
        item.setLockedStock(0);
        item.setTotalStock(10);
        item.setVersion(0);
        inventoryItemMapper.insert(item);
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        inventoryItemMapper.delete(
                new LambdaQueryWrapper<InventoryItem>().eq(InventoryItem::getSkuId, TEST_SKU_ID)
        );
        inventoryLogMapper.delete(
                new LambdaQueryWrapper<InventoryLog>().eq(InventoryLog::getSkuId, TEST_SKU_ID)
        );
    }

    private InventoryItem getTestItem() {
        return inventoryItemMapper.selectOne(
                new LambdaQueryWrapper<InventoryItem>().eq(InventoryItem::getSkuId, TEST_SKU_ID)
        );
    }

    @Test
    @DisplayName("正常扣减：库存10扣1个，库存变9，版本号变1")
    void deductStock_normal() {
        inventoryService.deductStock(TEST_SKU_ID, 1, "ORDER_NORMAL");

        InventoryItem item = getTestItem();
        assertNotNull(item);
        assertEquals(9, item.getAvailableStock());
        assertEquals(1, item.getVersion());
    }

    @Test
    @DisplayName("库存不足：扣11个抛 BizException(10031)")
    void deductStock_insufficientStock() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.deductStock(TEST_SKU_ID, 11, "ORDER_FAIL"));

        assertEquals(10031, ex.getCode());

        // 库存不变
        InventoryItem item = getTestItem();
        assertEquals(10, item.getAvailableStock());
        assertEquals(0, item.getVersion());
    }

    /**
     * 核心测试：50线程并发各扣1个，库存只有10
     *
     * CountDownLatch(1) 的作用：
     * 类似赛跑的发令枪——所有线程先就位（await），
     * 主线程 countDown 时所有线程同时冲出去，
     * 保证真正的并发竞争。
     *
     * 预期结果：
     * - 成功扣减恰好 10 次（库存扣完）
     * - 失败 40 次（BizException: 库存不足）
     * - 最终库存 = 0（不能为负数）
     * - 版本号 = 10（每次成功扣减 +1）
     */
    @Test
    @DisplayName("防超卖：50线程并发各扣1，库存10，成功恰好10次")
    void deductStock_concurrent_noOversell() throws InterruptedException {
        int threadCount = 50;
        int initialStock = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        // 发令枪：所有线程 await 住，等 countDown 后一起冲
        CountDownLatch gun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    gun.await(); // 等发令枪
                    inventoryService.deductStock(TEST_SKU_ID, 1, "CONC_ORDER_" + idx);
                    successCount.incrementAndGet();
                } catch (BizException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    finishLine.countDown();
                }
            });
        }

        gun.countDown();     // 开火！所有线程同时开始扣减
        finishLine.await();  // 等所有线程跑完
        pool.shutdown();

        InventoryItem item = getTestItem();

        System.out.println("========== 防超卖测试结果 ==========");
        System.out.println("成功次数: " + successCount.get());
        System.out.println("失败次数: " + failCount.get());
        System.out.println("最终可用库存: " + item.getAvailableStock());
        System.out.println("最终版本号: " + item.getVersion());
        System.out.println("====================================");

        assertEquals(initialStock, successCount.get(),
                "成功次数应该等于初始库存");
        assertEquals(threadCount - initialStock, failCount.get(),
                "剩余线程都应该失败");
        assertEquals(0, item.getAvailableStock(),
                "最终库存应为0");
        assertEquals(initialStock, item.getVersion(),
                "版本号应等于成功扣减次数");
    }

    @Test
    @DisplayName("预占库存：库存10预占3，可用变7，预占变3")
    void occupyStock_normal() {
        inventoryService.occupyStock(TEST_SKU_ID, 3, "ORDER_OCCUPY");

        InventoryItem item = getTestItem();
        assertEquals(7, item.getAvailableStock());
        assertEquals(3, item.getLockedStock());
        assertEquals(10, item.getTotalStock());
    }

    @Test
    @DisplayName("释放预占：先预占3再释放2，可用变9，预占变1")
    void releaseStock_normal() {
        inventoryService.occupyStock(TEST_SKU_ID, 3, "ORDER_REL");

        inventoryService.releaseStock(TEST_SKU_ID, 2, "ORDER_REL");

        InventoryItem item = getTestItem();
        assertEquals(9, item.getAvailableStock());
        assertEquals(1, item.getLockedStock());
    }

    @Test
    @DisplayName("确认扣减：先预占3再确认扣3，总库存变7")
    void confirmDeduct_normal() {
        inventoryService.occupyStock(TEST_SKU_ID, 3, "ORDER_CONF");

        inventoryService.confirmDeduct(TEST_SKU_ID, 3, "ORDER_CONF");

        InventoryItem item = getTestItem();
        assertEquals(7, item.getAvailableStock());
        assertEquals(0, item.getLockedStock());
        assertEquals(7, item.getTotalStock());
    }

    // ==================== 参数校验测试 ====================

    @Test
    @DisplayName("参数校验：扣减数量为0抛 BizException(10034)")
    void deductStock_quantityZero() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.deductStock(TEST_SKU_ID, 0, "ORDER_QTY"));

        assertEquals(10034, ex.getCode());

        InventoryItem item = getTestItem();
        assertEquals(10, item.getAvailableStock());
    }

    @Test
    @DisplayName("参数校验：扣减数量为负数抛 BizException(10034)")
    void deductStock_quantityNegative() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.deductStock(TEST_SKU_ID, -1, "ORDER_QTY"));

        assertEquals(10034, ex.getCode());
    }

    @Test
    @DisplayName("参数校验：预占数量为0抛 BizException(10034)")
    void occupyStock_quantityZero() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.occupyStock(TEST_SKU_ID, 0, "ORDER_QTY"));

        assertEquals(10034, ex.getCode());
    }

    @Test
    @DisplayName("参数校验：释放数量为0抛 BizException(10034)")
    void releaseStock_quantityZero() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.releaseStock(TEST_SKU_ID, 0, "ORDER_QTY"));

        assertEquals(10034, ex.getCode());
    }

    @Test
    @DisplayName("参数校验：确认扣减数量为0抛 BizException(10034)")
    void confirmDeduct_quantityZero() {
        BizException ex = assertThrows(BizException.class,
                () -> inventoryService.confirmDeduct(TEST_SKU_ID, 0, "ORDER_QTY"));

        assertEquals(10034, ex.getCode());
    }

    // ==================== 幂等性测试 ====================

    @Test
    @DisplayName("幂等：重复扣减同一订单，第二次跳过，库存只减1")
    void deductStock_idempotent() {
        String orderId = "ORDER_IDEMPOTENT";

        inventoryService.deductStock(TEST_SKU_ID, 1, orderId);
        inventoryService.deductStock(TEST_SKU_ID, 1, orderId);

        InventoryItem item = getTestItem();
        assertEquals(9, item.getAvailableStock());

        long logCount = inventoryLogMapper.selectCount(
                new LambdaQueryWrapper<InventoryLog>()
                        .eq(InventoryLog::getOrderId, orderId)
                        .eq(InventoryLog::getChangeType, "DEDUCT")
        );
        assertEquals(1, logCount, "流水记录应该只有1条");
    }

    @Test
    @DisplayName("幂等：重复预占同一订单，第二次跳过，库存只冻结1次")
    void occupyStock_idempotent() {
        String orderId = "ORDER_OCC_IDEMPOTENT";

        inventoryService.occupyStock(TEST_SKU_ID, 3, orderId);
        inventoryService.occupyStock(TEST_SKU_ID, 3, orderId);

        InventoryItem item = getTestItem();
        assertEquals(7, item.getAvailableStock());
        assertEquals(3, item.getLockedStock());

        long logCount = inventoryLogMapper.selectCount(
                new LambdaQueryWrapper<InventoryLog>()
                        .eq(InventoryLog::getOrderId, orderId)
                        .eq(InventoryLog::getChangeType, "OCCUPY")
        );
        assertEquals(1, logCount, "流水记录应该只有1条");
    }

    @Test
    @DisplayName("幂等：重复释放同一订单，第二次跳过")
    void releaseStock_idempotent() {
        String orderId = "ORDER_REL_IDEMPOTENT";

        inventoryService.occupyStock(TEST_SKU_ID, 3, orderId);
        inventoryService.releaseStock(TEST_SKU_ID, 3, orderId);
        inventoryService.releaseStock(TEST_SKU_ID, 3, orderId);

        InventoryItem item = getTestItem();
        assertEquals(10, item.getAvailableStock());
        assertEquals(0, item.getLockedStock());

        long logCount = inventoryLogMapper.selectCount(
                new LambdaQueryWrapper<InventoryLog>()
                        .eq(InventoryLog::getOrderId, orderId)
                        .eq(InventoryLog::getChangeType, "RELEASE")
        );
        assertEquals(1, logCount, "流水记录应该只有1条");
    }

    @Test
    @DisplayName("幂等：重复确认扣减同一订单，第二次跳过")
    void confirmDeduct_idempotent() {
        String orderId = "ORDER_CONF_IDEMPOTENT";

        inventoryService.occupyStock(TEST_SKU_ID, 3, orderId);
        inventoryService.confirmDeduct(TEST_SKU_ID, 3, orderId);
        inventoryService.confirmDeduct(TEST_SKU_ID, 3, orderId);

        InventoryItem item = getTestItem();
        assertEquals(7, item.getAvailableStock());
        assertEquals(0, item.getLockedStock());
        assertEquals(7, item.getTotalStock());

        long logCount = inventoryLogMapper.selectCount(
                new LambdaQueryWrapper<InventoryLog>()
                        .eq(InventoryLog::getOrderId, orderId)
                        .eq(InventoryLog::getChangeType, "CONFIRM")
        );
        assertEquals(1, logCount, "流水记录应该只有1条");
    }
}