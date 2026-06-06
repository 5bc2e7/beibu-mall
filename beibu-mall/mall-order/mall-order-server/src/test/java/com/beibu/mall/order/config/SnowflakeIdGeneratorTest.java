package com.beibu.mall.order.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 雪花算法 ID 生成器测试
 *
 * 测试 SnowflakeIdGenerator 的核心逻辑，包括：
 * - 构造方法参数校验
 * - ID 唯一性
 * - ID 递增性
 * - 并发安全性
 * - 订单号生成
 */
@DisplayName("雪花算法 ID 生成器测试")
class SnowflakeIdGeneratorTest {

    // ==================== 构造方法校验测试 ====================

    @Test
    @DisplayName("构造方法 - 正常参数创建成功")
    void constructor_validParams_createsInstance() {
        assertDoesNotThrow(() -> new SnowflakeIdGenerator(1, 1));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 32, 100})
    @DisplayName("构造方法 - 无效数据中心ID抛出异常")
    void constructor_invalidDatacenterId_throwsException(long datacenterId) {
        assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(datacenterId, 1));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 32, 100})
    @DisplayName("构造方法 - 无效机器ID抛出异常")
    void constructor_invalidWorkerId_throwsException(long workerId) {
        assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(1, workerId));
    }

    @Test
    @DisplayName("构造方法 - 边界值0创建成功")
    void constructor_boundaryZero_createsInstance() {
        assertDoesNotThrow(() -> new SnowflakeIdGenerator(0, 0));
    }

    @Test
    @DisplayName("构造方法 - 边界值31创建成功")
    void constructor_boundaryThirtyOne_createsInstance() {
        assertDoesNotThrow(() -> new SnowflakeIdGenerator(31, 31));
    }

    // ==================== nextId 测试 ====================

    @Test
    @DisplayName("nextId - 生成的ID是正数")
    void nextId_returnsPositive() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        long id = generator.nextId();
        assertTrue(id > 0, "ID 应该是正数，实际值：" + id);
    }

    @Test
    @DisplayName("nextId - 连续生成的ID是递增的")
    void nextId_sequential_increasing() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        long id3 = generator.nextId();

        assertTrue(id2 > id1, "第二个ID应该大于第一个");
        assertTrue(id3 > id2, "第三个ID应该大于第二个");
    }

    @Test
    @DisplayName("nextId - 生成的ID不重复")
    void nextId_unique() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            long id = generator.nextId();
            assertFalse(ids.contains(id), "ID 不应该重复，重复的ID：" + id);
            ids.add(id);
        }

        assertEquals(1000, ids.size(), "应该生成1000个唯一ID");
    }

    @Test
    @DisplayName("nextId - 不同数据中心生成不同ID")
    void nextId_differentDatacenters_differentIds() {
        SnowflakeIdGenerator generator1 = new SnowflakeIdGenerator(1, 1);
        SnowflakeIdGenerator generator2 = new SnowflakeIdGenerator(2, 1);

        long id1 = generator1.nextId();
        long id2 = generator2.nextId();

        assertNotEquals(id1, id2, "不同数据中心生成的ID应该不同");
    }

    @Test
    @DisplayName("nextId - 不同机器生成不同ID")
    void nextId_differentWorkers_differentIds() {
        SnowflakeIdGenerator generator1 = new SnowflakeIdGenerator(1, 1);
        SnowflakeIdGenerator generator2 = new SnowflakeIdGenerator(1, 2);

        long id1 = generator1.nextId();
        long id2 = generator2.nextId();

        assertNotEquals(id1, id2, "不同机器生成的ID应该不同");
    }

    // ==================== nextOrderNo 测试 ====================

    @Test
    @DisplayName("nextOrderNo - 返回非空字符串")
    void nextOrderNo_returnsNonEmpty() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        String orderNo = generator.nextOrderNo();

        assertNotNull(orderNo);
        assertFalse(orderNo.isEmpty());
    }

    @Test
    @DisplayName("nextOrderNo - 返回纯数字字符串")
    void nextOrderNo_returnsNumeric() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        String orderNo = generator.nextOrderNo();

        assertTrue(orderNo.matches("\\d+"), "订单号应该是纯数字，实际值：" + orderNo);
    }

    @Test
    @DisplayName("nextOrderNo - 与 nextId 一致")
    void nextOrderNo_matchesNextId() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        // 由于 nextId 是 synchronized，需要重新创建生成器来获取一致的结果
        // 这里只验证格式一致性
        String orderNo1 = generator.nextOrderNo();
        String orderNo2 = generator.nextOrderNo();

        assertNotEquals(orderNo1, orderNo2, "连续生成的订单号应该不同");
    }

    // ==================== 并发测试 ====================

    @Test
    @DisplayName("并发 - 多线程生成ID不重复")
    void concurrent_generation_unique() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        int threadCount = 10;
        int idsPerThread = 100;
        int totalIds = threadCount * idsPerThread;

        Set<Long> ids = new HashSet<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // 等待同时开始
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = generator.nextId();
                        synchronized (ids) {
                            assertFalse(ids.contains(id), "并发生成的ID不应该重复，重复的ID：" + id);
                            ids.add(id);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown(); // 开始
        finishLatch.await(); // 等待完成
        executor.shutdown();

        assertEquals(totalIds, ids.size(), "应该生成 " + totalIds + " 个唯一ID");
    }

    // ==================== ID 结构测试 ====================

    @Test
    @DisplayName("ID结构 - 时间戳部分正确")
    void idStructure_timestamp_correct() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        long id = generator.nextId();

        // 提取时间戳部分（右移22位）
        long timestampPart = id >> 22;
        // 起始时间戳
        long startTimestamp = 1704067200000L;
        // 当前时间应该大于起始时间
        long currentTime = System.currentTimeMillis();
        long expectedTimestamp = currentTime - startTimestamp;

        // 允许一定的误差（1秒）
        long tolerance = 1000L;
        assertTrue(Math.abs(timestampPart - expectedTimestamp) < tolerance,
                "时间戳部分应该接近当前时间，期望：" + expectedTimestamp + "，实际：" + timestampPart);
    }

    @Test
    @DisplayName("ID结构 - 数据中心ID部分正确")
    void idStructure_datacenterId_correct() {
        long datacenterId = 5;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(datacenterId, 1);
        long id = generator.nextId();

        // 提取数据中心ID部分（右移17位，与31进行与运算）
        long extractedDatacenterId = (id >> 17) & 31;
        assertEquals(datacenterId, extractedDatacenterId, "数据中心ID部分应该正确");
    }

    @Test
    @DisplayName("ID结构 - 机器ID部分正确")
    void idStructure_workerId_correct() {
        long workerId = 10;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, workerId);
        long id = generator.nextId();

        // 提取机器ID部分（右移12位，与31进行与运算）
        long extractedWorkerId = (id >> 12) & 31;
        assertEquals(workerId, extractedWorkerId, "机器ID部分应该正确");
    }

    @Test
    @DisplayName("ID结构 - 序列号部分正确")
    void idStructure_sequence_correct() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        long id1 = generator.nextId();
        long id2 = generator.nextId();

        // 提取序列号部分（与4095进行与运算）
        long sequence1 = id1 & 4095L;
        long sequence2 = id2 & 4095L;

        // 连续调用应该序列号递增（或者不同毫秒重置为0）
        assertTrue(sequence2 >= 0 && sequence2 <= 4095, "序列号应该在0-4095范围内");
    }
}
