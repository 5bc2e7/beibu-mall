package com.beibu.mall.seckill;

import com.beibu.mall.seckill.config.RedisKeyConstants;
import com.beibu.mall.seckill.dto.SeckillRequestDTO;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.service.SeckillService;
import com.beibu.mall.seckill.vo.SeckillResultVO;
import com.beibu.mall.seckill.mq.SeckillMessage;
import com.beibu.mall.seckill.mq.SeckillMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 秒杀服务集成测试
 *
 * 验证并发抢购不超卖
 *
 * 测试场景：
 * - 10 个库存
 * - 20 个用户同时抢购
 * - 预期：恰好 10 个成功，10 个失败
 */
@SpringBootTest
@ActiveProfiles("test")
public class SeckillServiceTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SeckillMessageProducer seckillMessageProducer;

    private static final Long ACTIVITY_ID = 1001L;
    private static final int STOCK = 10;
    private static final int USER_COUNT = 20;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM seckill_activity WHERE id = " + ACTIVITY_ID);

        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setActivityName("帝王蟹限时秒杀");
        activity.setProductId(1001L);
        activity.setProductName("北海道帝王蟹 2.5-3斤/只");
        activity.setProductImage("https://example.com/king-crab.jpg");
        activity.setOriginalPrice(new BigDecimal("599.00"));
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

        redisTemplate.delete(RedisKeyConstants.getStockKey(ACTIVITY_ID));
        redisTemplate.delete(RedisKeyConstants.getBoughtKey(ACTIVITY_ID));

        seckillService.warmUpStock(ACTIVITY_ID);
    }

    /**
     * 测试并发抢购不超卖
     *
     * 使用 CountDownLatch 模拟并发
     * CountDownLatch：倒计时门栓，让所有线程同时开始
     */
    @Test
    void testConcurrentSeckill() throws InterruptedException {
        // 计数器
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 线程池
        ExecutorService executor = Executors.newFixedThreadPool(USER_COUNT);

        // 门栓：让所有线程同时开始
        CountDownLatch latch = new CountDownLatch(1);

        // 提交 20 个任务
        for (int i = 1; i <= USER_COUNT; i++) {
            final Long userId = (long) i;
            executor.submit(() -> {
                try {
                    // 等待门栓打开（所有线程同时开始）
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

        // 打开门栓，所有线程同时开始
        latch.countDown();

        // 等待所有任务完成
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 验证结果
        System.out.println("=== 秒杀测试结果 ===");
        System.out.println("成功人数: " + successCount.get());
        System.out.println("失败人数: " + failCount.get());

        // 断言：成功人数应该等于库存数
        assertEquals(STOCK, successCount.get(), "成功人数应该等于库存数");
        assertEquals(USER_COUNT - STOCK, failCount.get(), "失败人数应该等于总人数减去库存数");

        // 验证 Redis 中的库存应该为 0
        String stockKey = RedisKeyConstants.getStockKey(ACTIVITY_ID);
        Object remainingStock = redisTemplate.opsForValue().get(stockKey);
        assertEquals(0, remainingStock, "Redis 中的库存应该为 0");

        verify(seckillMessageProducer, times(STOCK)).sendMessage(any(SeckillMessage.class));
    }

    /**
     * 测试重复抢购
     *
     * 同一个用户抢两次，第二次应该失败
     */
    @Test
    void testDuplicateSeckill() {
        Long userId = 100L;

        SeckillRequestDTO request = new SeckillRequestDTO();
        request.setActivityId(ACTIVITY_ID);

        // 第一次抢购
        SeckillResultVO result1 = seckillService.doSeckill(request, userId);
        assertTrue(result1.getSuccess(), "第一次抢购应该成功");

        // 第二次抢购
        SeckillResultVO result2 = seckillService.doSeckill(request, userId);
        assertFalse(result2.getSuccess(), "第二次抢购应该失败");
        assertEquals("您已经抢购过了，请勿重复下单", result2.getMessage());
    }
}
