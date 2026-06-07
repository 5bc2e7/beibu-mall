package com.beibu.mall.seckill.mq;

import com.beibu.mall.seckill.config.RedisKeyConstants;
import com.beibu.mall.seckill.dto.SeckillRequestDTO;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.entity.SeckillOrder;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.mapper.SeckillOrderMapper;
import com.beibu.mall.seckill.service.SeckillService;
import com.beibu.mall.seckill.vo.SeckillResultStatus;
import com.beibu.mall.seckill.vo.SeckillResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RocketMQ 集成测试
 *
 * 使用 Testcontainers 启动真实的 RocketMQ 容器，验证秒杀消息能正常发送和接收。
 *
 * 测试场景：
 * - 发送秒杀消息 → 验证消息能被消费
 * - 重复抢购 → 验证幂等处理
 * - 并发抢购 → 验证库存不超卖
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration," +
                "com.alibaba.cloud.nacos.NacosServiceAutoConfiguration," +
                "com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration"
})
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SeckillMqIntegrationTest {

    @Container
    static RocketMQTestContainer rocketmq = new RocketMQTestContainer();

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Long ACTIVITY_ID = 2001L;
    private static final int STOCK = 5;

    @DynamicPropertySource
    static void configureRocketMQ(DynamicPropertyRegistry registry) {
        registry.add("rocketmq.name-server", rocketmq::getNamesrvAddr);
        registry.add("rocketmq.producer.group", () -> "test-producer-group");
    }

    @BeforeEach
    void setUp() {
        // 等待 RocketMQ 完全就绪
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertTrue(rocketmq.isRunning());
                });

        // 创建 Topic
        rocketmq.createTopic("seckill-topic");

        // 等待前一个测试的消费者处理完成（排水机制）
        // 通过检查订单数量在多次轮询中保持稳定来确认没有 in-flight 消息
        drainPendingMessages();

        // 清理测试数据
        jdbcTemplate.execute("DELETE FROM seckill_order WHERE activity_id = " + ACTIVITY_ID);
        jdbcTemplate.execute("DELETE FROM seckill_activity WHERE id = " + ACTIVITY_ID);

        // 准备测试数据
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setActivityName("集成测试秒杀活动");
        activity.setProductId(2001L);
        activity.setProductName("测试商品");
        activity.setProductImage("https://example.com/test.jpg");
        activity.setOriginalPrice(new BigDecimal("199.00"));
        activity.setSeckillPrice(new BigDecimal("99.00"));
        activity.setTotalStock(STOCK);
        activity.setAvailableStock(STOCK);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setStatus(1);
        activity.setCreateTime(LocalDateTime.now());
        activity.setUpdateTime(LocalDateTime.now());
        activity.setDeleted(0);
        seckillActivityMapper.insert(activity);

        // 清理 Redis（包括结果 key）
        redisTemplate.delete("seckill:stock:" + ACTIVITY_ID);
        redisTemplate.delete("seckill:bought:" + ACTIVITY_ID);
        // 清理所有 seckill:result:* keys
        Set<String> resultKeys = redisTemplate.keys("seckill:result:*");
        if (resultKeys != null && !resultKeys.isEmpty()) {
            redisTemplate.delete(resultKeys);
        }

        // 预热库存
        seckillService.warmUpStock(ACTIVITY_ID);
    }

    /**
     * 测试发送秒杀消息
     *
     * 验证消息能正常发送到 RocketMQ
     */
    @Test
    @Order(1)
    void testSendSeckillMessage() {
        // Given
        SeckillRequestDTO request = new SeckillRequestDTO();
        request.setActivityId(ACTIVITY_ID);

        // When
        SeckillResultVO result = seckillService.doSeckill(request, 1L);

        // Then
        assertTrue(result.getSuccess(), "秒杀应该成功");
        String token = result.getToken();
        assertNotNull(token, "应该返回查询 token");

        // 验证库存已扣减
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    String stockKey = "seckill:stock:" + ACTIVITY_ID;
                    Object remainingStock = redisTemplate.opsForValue().get(stockKey);
                    assertNotNull(remainingStock, "Redis 中应该有库存数据");
                    assertEquals(STOCK - 1, Integer.parseInt(remainingStock.toString()),
                            "库存应该减少 1");
                });

        // 验证消费者创建了订单
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    SeckillOrder order = seckillOrderMapper.selectOne(
                            new LambdaQueryWrapper<SeckillOrder>()
                                    .eq(SeckillOrder::getUserId, 1L)
                                    .eq(SeckillOrder::getActivityId, ACTIVITY_ID));
                    assertNotNull(order, "消费者应该已创建订单");
                    assertEquals(0, order.getOrderStatus(), "订单状态应为待支付");
                });

        // 验证结果状态被消费者更新为 SUCCESS
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    String resultKey = RedisKeyConstants.getResultKey(token);
                    Object cachedResult = redisTemplate.opsForValue().get(resultKey);
                    assertNotNull(cachedResult, "结果状态应该存在");
                    SeckillResultStatus resultStatus = objectMapper.convertValue(cachedResult, SeckillResultStatus.class);
                    assertEquals("SUCCESS", resultStatus.getStatus(), "结果状态应为 SUCCESS");
                    assertNotNull(resultStatus.getOrderId(), "应该有订单 ID");
                });
    }

    /**
     * 测试重复抢购
     *
     * 同一个用户抢两次，第二次应该失败
     */
    @Test
    @Order(2)
    void testDuplicateSeckill() {
        // Given
        Long userId = 100L;
        SeckillRequestDTO request = new SeckillRequestDTO();
        request.setActivityId(ACTIVITY_ID);

        // When - 第一次抢购
        SeckillResultVO result1 = seckillService.doSeckill(request, userId);

        // Then - 第一次应该成功
        assertTrue(result1.getSuccess(), "第一次抢购应该成功");

        // 验证消费者创建了订单
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    SeckillOrder order = seckillOrderMapper.selectOne(
                            new LambdaQueryWrapper<SeckillOrder>()
                                    .eq(SeckillOrder::getUserId, userId)
                                    .eq(SeckillOrder::getActivityId, ACTIVITY_ID));
                    assertNotNull(order, "消费者应该已创建订单");
                });

        // When - 第二次抢购
        SeckillResultVO result2 = seckillService.doSeckill(request, userId);

        // Then - 第二次应该失败（幂等）
        assertFalse(result2.getSuccess(), "第二次抢购应该失败");
        assertEquals("您已经抢购过了，请勿重复下单", result2.getMessage());
    }

    /**
     * 测试并发抢购不超卖
     *
     * 多个用户同时抢购，验证库存不会超卖
     */
    @Test
    @Order(3)
    void testConcurrentSeckill() throws InterruptedException {
        // Given
        int userCount = 10;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(userCount);

        // When - 并发抢购
        for (int i = 1; i <= userCount; i++) {
            final Long userId = (long) i;
            executor.submit(() -> {
                try {
                    latch.await();
                    SeckillRequestDTO request = new SeckillRequestDTO();
                    request.setActivityId(ACTIVITY_ID);
                    SeckillResultVO result = seckillService.doSeckill(request, userId);
                    if (result.getSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Then - 验证结果
        assertEquals(STOCK, successCount.get(), "成功人数应该等于库存数");
        assertEquals(userCount - STOCK, failCount.get(), "失败人数应该等于总人数减去库存数");

        // 验证 Redis 中的库存应该为 0
        String stockKey = "seckill:stock:" + ACTIVITY_ID;
        Object remainingStock = redisTemplate.opsForValue().get(stockKey);
        assertEquals(0, remainingStock, "Redis 中的库存应该为 0");

        // 验证消费者创建了所有订单
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Long orderCount = seckillOrderMapper.selectCount(
                            new LambdaQueryWrapper<SeckillOrder>()
                                    .eq(SeckillOrder::getActivityId, ACTIVITY_ID));
                    assertEquals((long) STOCK, orderCount, "消费者应该创建了 " + STOCK + " 个订单");
                });
    }

    /**
     * 排水机制：等待前一个测试的消费者处理完成
     * 通过检查订单数量在多次轮询中保持稳定来确认没有 in-flight 消息
     */
    private void drainPendingMessages() {
        Long previousCount = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getActivityId, ACTIVITY_ID));
        int stablePolls = 0;
        int requiredStablePolls = 3;
        int maxPolls = 15;
        int pollCount = 0;

        while (stablePolls < requiredStablePolls && pollCount < maxPolls) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Long currentCount = seckillOrderMapper.selectCount(
                    new LambdaQueryWrapper<SeckillOrder>()
                            .eq(SeckillOrder::getActivityId, ACTIVITY_ID));
            if (currentCount.equals(previousCount)) {
                stablePolls++;
            } else {
                stablePolls = 0;
                previousCount = currentCount;
            }
            pollCount++;
        }
    }
}
