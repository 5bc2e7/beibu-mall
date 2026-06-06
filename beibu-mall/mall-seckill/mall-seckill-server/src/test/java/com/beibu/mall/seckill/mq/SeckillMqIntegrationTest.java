package com.beibu.mall.seckill.mq;

import com.beibu.mall.seckill.dto.SeckillRequestDTO;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.service.SeckillService;
import com.beibu.mall.seckill.vo.SeckillResultVO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * - 发送重复消息 → 验证幂等处理
 *
 * 注意：
 * - 这个测试会比较慢（容器启动需要时间），用 @Tag("integration") 标记
 * - 只在本地跑，CI 里可以用 -DexcludeGroups=integration 跳过
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class SeckillMqIntegrationTest {

    private static final String ROCKETMQ_IMAGE = "apache/rocketmq:5.3.1";
    private static final String NAMESERVER_ALIAS = "nameserver";
    private static final int NAMESERVER_PORT = 9876;
    private static final int BROKER_PORT = 10911;

    private static final Network network = Network.newNetwork();

    @Container
    static GenericContainer<?> nameserver = new GenericContainer<>(DockerImageName.parse(ROCKETMQ_IMAGE))
            .withNetwork(network)
            .withNetworkAliases(NAMESERVER_ALIAS)
            .withExposedPorts(NAMESERVER_PORT)
            .withCommand("sh", "mqnamesrv");

    @Container
    static GenericContainer<?> broker = new GenericContainer<>(DockerImageName.parse(ROCKETMQ_IMAGE))
            .withNetwork(network)
            .withExposedPorts(BROKER_PORT, 10909, 10912)
            .withEnv("NAMESRV_ADDR", NAMESERVER_ALIAS + ":" + NAMESERVER_PORT)
            .withCommand("sh", "-c", buildBrokerStartCommand())
            .dependsOn(nameserver);

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    private static final Long ACTIVITY_ID = 2001L;
    private static final int STOCK = 5;

    @DynamicPropertySource
    static void configureRocketMQ(DynamicPropertyRegistry registry) {
        String nameServerAddr = nameserver.getHost() + ":" + nameserver.getMappedPort(NAMESERVER_PORT);
        registry.add("rocketmq.name-server", () -> nameServerAddr);
        registry.add("rocketmq.producer.group", () -> "test-producer-group");
    }

    private static String buildBrokerStartCommand() {
        return "cat > /tmp/broker.conf <<EOF\n" +
                "brokerClusterName=DefaultCluster\n" +
                "brokerName=broker-a\n" +
                "brokerId=0\n" +
                "deleteWhen=04\n" +
                "fileReservedTime=48\n" +
                "brokerRole=ASYNC_MASTER\n" +
                "flushDiskType=ASYNC_FLUSH\n" +
                "listenPort=" + BROKER_PORT + "\n" +
                "autoCreateTopicEnable=true\n" +
                "autoCreateSubscriptionGroup=true\n" +
                "EOF\n" +
                "sh mqbroker -n " + NAMESERVER_ALIAS + ":" + NAMESERVER_PORT + " -c /tmp/broker.conf";
    }

    @BeforeEach
    void setUp() {
        // 等待 RocketMQ 就绪
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // 验证 NameServer 可达
                    assertTrue(nameserver.isRunning());
                    assertTrue(broker.isRunning());
                });

        // 清理测试数据
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

        // 清理 Redis
        redisTemplate.delete("seckill:stock:" + ACTIVITY_ID);
        redisTemplate.delete("seckill:bought:" + ACTIVITY_ID);

        // 预热库存
        seckillService.warmUpStock(ACTIVITY_ID);
    }

    /**
     * 测试发送秒杀消息
     *
     * 验证消息能正常发送到 RocketMQ
     */
    @Test
    void testSendSeckillMessage() {
        // Given
        SeckillRequestDTO request = new SeckillRequestDTO();
        request.setActivityId(ACTIVITY_ID);

        // When
        SeckillResultVO result = seckillService.doSeckill(request, 1L);

        // Then
        assertTrue(result.getSuccess(), "秒杀应该成功");

        // 验证消息已发送（通过检查库存变化）
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    String stockKey = "seckill:stock:" + ACTIVITY_ID;
                    Object remainingStock = redisTemplate.opsForValue().get(stockKey);
                    assertNotNull(remainingStock, "Redis 中应该有库存数据");
                    assertEquals(STOCK - 1, Integer.parseInt(remainingStock.toString()),
                            "库存应该减少 1");
                });
    }

    /**
     * 测试重复抢购
     *
     * 同一个用户抢两次，第二次应该失败
     */
    @Test
    void testDuplicateSeckill() {
        // Given
        Long userId = 100L;
        SeckillRequestDTO request = new SeckillRequestDTO();
        request.setActivityId(ACTIVITY_ID);

        // When - 第一次抢购
        SeckillResultVO result1 = seckillService.doSeckill(request, userId);

        // Then - 第一次应该成功
        assertTrue(result1.getSuccess(), "第一次抢购应该成功");

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
    }
}
