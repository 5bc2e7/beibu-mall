package com.beibu.mall.seckill.service;

import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.seckill.config.RedisKeyConstants;
import com.beibu.mall.seckill.dto.SeckillRequestDTO;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.entity.SeckillOrder;
import com.beibu.mall.seckill.enums.OrderStatus;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.mapper.SeckillOrderMapper;
import com.beibu.mall.seckill.mq.SeckillMessageProducer;
import com.beibu.mall.seckill.service.impl.SeckillServiceImpl;
import com.beibu.mall.seckill.vo.SeckillOrderVO;
import com.beibu.mall.seckill.vo.SeckillResultStatus;
import com.beibu.mall.seckill.vo.SeckillResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SeckillServiceImpl 单元测试（Mockito）
 *
 * 覆盖所有公共方法的边界情况和异常路径
 * 与 SeckillServiceTest（集成测试）互补，专注于逻辑分支覆盖
 */
@ExtendWith(MockitoExtension.class)
class SeckillServiceImplCoverageTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private DefaultRedisScript<Long> seckillLuaScript;

    @Mock
    private SeckillActivityMapper seckillActivityMapper;

    @Mock
    private SeckillOrderMapper seckillOrderMapper;

    @Mock
    private SeckillMessageProducer seckillMessageProducer;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private SeckillServiceImpl seckillService;

    private static final Long ACTIVITY_ID = 1001L;
    private static final Long USER_ID = 100L;
    private static final Long ORDER_ID = 2001L;

    private SeckillActivity activeActivity;
    private SeckillRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        // 通用 mock 设置（lenient 避免未使用的 stub 报错）
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);

        // 构建一个有效的进行中活动
        activeActivity = buildActiveActivity();

        // 构建秒杀请求
        requestDTO = new SeckillRequestDTO();
        requestDTO.setActivityId(ACTIVITY_ID);
    }

    private SeckillActivity buildActiveActivity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setActivityName("帝王蟹限时秒杀");
        activity.setProductId(1001L);
        activity.setProductName("北海道帝王蟹 2.5-3斤/只");
        activity.setProductImage("https://example.com/king-crab.jpg");
        activity.setOriginalPrice(new BigDecimal("599.00"));
        activity.setSeckillPrice(new BigDecimal("99.00"));
        activity.setTotalStock(100);
        activity.setAvailableStock(50);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setStatus(1);
        activity.setCreateTime(LocalDateTime.now());
        activity.setUpdateTime(LocalDateTime.now());
        activity.setDeleted(0);
        return activity;
    }

    private SeckillOrder buildOrder(int orderStatus) {
        SeckillOrder order = new SeckillOrder();
        order.setId(ORDER_ID);
        order.setActivityId(ACTIVITY_ID);
        order.setUserId(USER_ID);
        order.setProductId(1001L);
        order.setProductName("北海道帝王蟹 2.5-3斤/只");
        order.setSeckillPrice(new BigDecimal("99.00"));
        order.setOrderStatus(orderStatus);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        return order;
    }

    // ==================== warmUpStock 测试 ====================

    @Nested
    @DisplayName("warmUpStock - 库存预热")
    class WarmUpStockTests {

        @Test
        @DisplayName("活动不存在时应抛出 BizException")
        void warmUpStock_activityNotFound_throwsBizException() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> seckillService.warmUpStock(ACTIVITY_ID))
                    .isInstanceOf(BizException.class)
                    .hasMessage("活动不存在");
        }

        @Test
        @DisplayName("活动存在且状态为未开始时，应预热库存并清除已购记录")
        void warmUpStock_statusZero_clearsBoughtSet() {
            // Given
            activeActivity.setStatus(0);
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);

            // When
            seckillService.warmUpStock(ACTIVITY_ID);

            // Then
            verify(valueOperations).set(
                    eq(RedisKeyConstants.getStockKey(ACTIVITY_ID)),
                    eq(50),
                    anyLong(),
                    eq(TimeUnit.SECONDS)
            );
            verify(redisTemplate).delete(RedisKeyConstants.getBoughtKey(ACTIVITY_ID));
        }

        @Test
        @DisplayName("活动存在且状态为进行中时，应预热库存但不清除已购记录")
        void warmUpStock_statusOne_doesNotClearBoughtSet() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);

            // When
            seckillService.warmUpStock(ACTIVITY_ID);

            // Then
            verify(valueOperations).set(
                    eq(RedisKeyConstants.getStockKey(ACTIVITY_ID)),
                    eq(50),
                    anyLong(),
                    eq(TimeUnit.SECONDS)
            );
            verify(redisTemplate, never()).delete(anyString());
        }
    }

    // ==================== doSeckill 测试 ====================

    @Nested
    @DisplayName("doSeckill - 执行秒杀")
    class DoSeckillTests {

        @Test
        @DisplayName("活动不存在时应抛出 BizException")
        void doSeckill_activityNotFound_throwsBizException() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> seckillService.doSeckill(requestDTO, USER_ID))
                    .isInstanceOf(BizException.class)
                    .hasMessage("活动不存在");
        }

        @Test
        @DisplayName("活动状态不是进行中时应抛出 BizException")
        void doSeckill_activityNotActive_throwsBizException() {
            // Given
            activeActivity.setStatus(0); // 未开始
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);

            // When & Then
            assertThatThrownBy(() -> seckillService.doSeckill(requestDTO, USER_ID))
                    .isInstanceOf(BizException.class)
                    .hasMessage("活动未在进行中");
        }

        @Test
        @DisplayName("活动尚未开始时应抛出 BizException")
        void doSeckill_activityNotStarted_throwsBizException() {
            // Given
            activeActivity.setStartTime(LocalDateTime.now().plusHours(1));
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);

            // When & Then
            assertThatThrownBy(() -> seckillService.doSeckill(requestDTO, USER_ID))
                    .isInstanceOf(BizException.class)
                    .hasMessage("活动尚未开始");
        }

        @Test
        @DisplayName("活动已结束时应抛出 BizException")
        void doSeckill_activityEnded_throwsBizException() {
            // Given
            activeActivity.setEndTime(LocalDateTime.now().minusHours(1));
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);

            // When & Then
            assertThatThrownBy(() -> seckillService.doSeckill(requestDTO, USER_ID))
                    .isInstanceOf(BizException.class)
                    .hasMessage("活动已结束");
        }

        @Test
        @DisplayName("Lua 脚本返回 null 时应抛出 BizException")
        void doSeckill_luaReturnsNull_throwsBizException() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> seckillService.doSeckill(requestDTO, USER_ID))
                    .isInstanceOf(BizException.class)
                    .hasMessage("系统异常，请稍后重试");
        }

        @Test
        @DisplayName("Lua 脚本返回 1 时应返回库存已售罄")
        void doSeckill_luaReturnsOne_returnsSoldOut() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);

            // When
            SeckillResultVO result = seckillService.doSeckill(requestDTO, USER_ID);

            // Then
            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("库存已售罄");
            assertThat(result.getActivityId()).isEqualTo(ACTIVITY_ID);
        }

        @Test
        @DisplayName("Lua 脚本返回 2 时应返回重复抢购提示")
        void doSeckill_luaReturnsTwo_returnsDuplicate() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(2L);

            // When
            SeckillResultVO result = seckillService.doSeckill(requestDTO, USER_ID);

            // Then
            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("您已经抢购过了，请勿重复下单");
            assertThat(result.getActivityId()).isEqualTo(ACTIVITY_ID);
        }

        @Test
        @DisplayName("Lua 脚本返回 0 且 MQ 发送成功时应返回秒杀成功")
        void doSeckill_luaReturnsZero_mqSuccess_returnsSuccess() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(0L);

            // When
            SeckillResultVO result = seckillService.doSeckill(requestDTO, USER_ID);

            // Then
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getToken()).isNotNull().isNotEmpty();
            assertThat(result.getMessage()).isEqualTo("抢购成功，请稍后查询结果");
            assertThat(result.getActivityId()).isEqualTo(ACTIVITY_ID);
            assertThat(result.getProductName()).isEqualTo("北海道帝王蟹 2.5-3斤/只");
            assertThat(result.getSeckillPrice()).isEqualByComparingTo(new BigDecimal("99.00"));

            // 验证 MQ 消息已发送
            verify(seckillMessageProducer).sendMessage(any());
            // 验证 PROCESSING 状态已写入 Redis
            verify(valueOperations).set(
                    anyString(),
                    argThat(obj -> {
                        if (obj instanceof SeckillResultStatus status) {
                            return "PROCESSING".equals(status.getStatus());
                        }
                        return false;
                    }),
                    eq(RedisKeyConstants.RESULT_TTL_HOURS),
                    eq(TimeUnit.HOURS)
            );
        }

        @Test
        @DisplayName("MQ 发送失败时应回滚 Redis 库存并抛出 BizException")
        void doSeckill_mqFailure_rollbackAndThrow() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);
            when(redisTemplate.execute(any(), anyList(), any())).thenReturn(0L);
            doThrow(new RuntimeException("MQ 连接失败"))
                    .when(seckillMessageProducer).sendMessage(any());

            // When & Then
            assertThatThrownBy(() -> seckillService.doSeckill(requestDTO, USER_ID))
                    .isInstanceOf(BizException.class)
                    .hasMessage("系统繁忙，请稍后重试");

            // 验证回滚操作：库存 +1
            verify(valueOperations).increment(RedisKeyConstants.getStockKey(ACTIVITY_ID));
            // 验证回滚操作：从已购集合移除用户
            verify(setOperations).remove(
                    RedisKeyConstants.getBoughtKey(ACTIVITY_ID),
                    USER_ID.toString()
            );
            // 验证删除结果 key
            verify(redisTemplate).delete(anyString());
        }
    }

    // ==================== querySeckillResult 测试 ====================

    @Nested
    @DisplayName("querySeckillResult - 查询秒杀结果")
    class QuerySeckillResultTests {

        @Test
        @DisplayName("Redis 中状态为 SUCCESS 时应返回订单信息")
        void querySeckillResult_successStatus_returnsOrderInfo() {
            // Given
            String token = "test-token";
            String resultKey = RedisKeyConstants.getResultKey(token);

            SeckillResultStatus cachedStatus = new SeckillResultStatus();
            cachedStatus.setStatus("SUCCESS");
            cachedStatus.setOrderId(ORDER_ID);
            cachedStatus.setMessage("下单成功");
            when(valueOperations.get(resultKey)).thenReturn(cachedStatus);

            // When
            SeckillOrderVO result = seckillService.querySeckillResult(token);

            // Then
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(result.getMessage()).isEqualTo("下单成功");
        }

        @Test
        @DisplayName("Redis 中状态为 PROCESSING 时应返回处理中")
        void querySeckillResult_processingStatus_returnsProcessing() {
            // Given
            String token = "test-token";
            String resultKey = RedisKeyConstants.getResultKey(token);

            SeckillResultStatus cachedStatus = new SeckillResultStatus();
            cachedStatus.setStatus("PROCESSING");
            cachedStatus.setMessage("订单处理中");
            when(valueOperations.get(resultKey)).thenReturn(cachedStatus);

            // When
            SeckillOrderVO result = seckillService.querySeckillResult(token);

            // Then
            assertThat(result.getStatus()).isEqualTo("PROCESSING");
            assertThat(result.getMessage()).isEqualTo("订单处理中，请稍后查询");
        }

        @Test
        @DisplayName("Redis 中状态为 FAILED 时应返回失败信息")
        void querySeckillResult_failedStatus_returnsFailed() {
            // Given
            String token = "test-token";
            String resultKey = RedisKeyConstants.getResultKey(token);

            SeckillResultStatus cachedStatus = new SeckillResultStatus();
            cachedStatus.setStatus("FAILED");
            cachedStatus.setMessage("库存不足，下单失败");
            when(valueOperations.get(resultKey)).thenReturn(cachedStatus);

            // When
            SeckillOrderVO result = seckillService.querySeckillResult(token);

            // Then
            assertThat(result.getStatus()).isEqualTo("FAILED");
            assertThat(result.getMessage()).isEqualTo("库存不足，下单失败");
        }

        @Test
        @DisplayName("Redis 中无缓存时应返回 NOT_FOUND")
        void querySeckillResult_cacheExpired_returnsNotFound() {
            // Given
            String token = "expired-token";
            String resultKey = RedisKeyConstants.getResultKey(token);
            when(valueOperations.get(resultKey)).thenReturn(null);

            // When
            SeckillOrderVO result = seckillService.querySeckillResult(token);

            // Then
            assertThat(result.getStatus()).isEqualTo("NOT_FOUND");
            assertThat(result.getMessage()).isEqualTo("查询结果不存在或已过期");
        }

        @Test
        @DisplayName("Redis 中数据解析异常时应返回 UNKNOWN")
        void querySeckillResult_parseException_returnsUnknown() {
            // Given
            String token = "test-token";
            String resultKey = RedisKeyConstants.getResultKey(token);
            // 返回一个无法转换为 SeckillResultStatus 的对象
            when(valueOperations.get(resultKey)).thenReturn("invalid_data");

            // When
            SeckillOrderVO result = seckillService.querySeckillResult(token);

            // Then
            assertThat(result.getStatus()).isEqualTo("UNKNOWN");
            assertThat(result.getMessage()).isEqualTo("未知状态");
        }
    }

    // ==================== getActivity 测试 ====================

    @Nested
    @DisplayName("getActivity - 查询活动信息")
    class GetActivityTests {

        @Test
        @DisplayName("活动存在时应返回活动信息")
        void getActivity_found_returnsActivity() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity);

            // When
            SeckillActivity result = seckillService.getActivity(ACTIVITY_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(ACTIVITY_ID);
            assertThat(result.getProductName()).isEqualTo("北海道帝王蟹 2.5-3斤/只");
        }

        @Test
        @DisplayName("活动不存在时应返回 null")
        void getActivity_notFound_returnsNull() {
            // Given
            when(seckillActivityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

            // When
            SeckillActivity result = seckillService.getActivity(ACTIVITY_ID);

            // Then
            assertThat(result).isNull();
        }
    }

    // ==================== getOrderDetail 测试 ====================

    @Nested
    @DisplayName("getOrderDetail - 查询订单详情")
    class GetOrderDetailTests {

        @Test
        @DisplayName("订单状态为 0 时应返回 PENDING_PAYMENT")
        void getOrderDetail_statusZero_returnsPendingPayment() {
            // Given
            SeckillOrder order = buildOrder(0);
            when(seckillOrderMapper.selectById(ORDER_ID)).thenReturn(order);

            // When
            SeckillOrderVO result = seckillService.getOrderDetail(ORDER_ID, USER_ID);

            // Then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(result.getProductName()).isEqualTo("北海道帝王蟹 2.5-3斤/只");
            assertThat(result.getSeckillPrice()).isEqualByComparingTo(new BigDecimal("99.00"));
            assertThat(result.getMessage()).isEqualTo("订单详情");
        }

        @Test
        @DisplayName("订单状态为 1 时应返回 PAID")
        void getOrderDetail_statusOne_returnsPaid() {
            // Given
            SeckillOrder order = buildOrder(1);
            when(seckillOrderMapper.selectById(ORDER_ID)).thenReturn(order);

            // When
            SeckillOrderVO result = seckillService.getOrderDetail(ORDER_ID, USER_ID);

            // Then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID.name());
            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("订单状态为 2 时应返回 CANCELLED")
        void getOrderDetail_statusTwo_returnsCancelled() {
            // Given
            SeckillOrder order = buildOrder(2);
            when(seckillOrderMapper.selectById(ORDER_ID)).thenReturn(order);

            // When
            SeckillOrderVO result = seckillService.getOrderDetail(ORDER_ID, USER_ID);

            // Then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("订单状态为未知值时应返回 UNKNOWN")
        void getOrderDetail_unknownStatus_returnsUnknown() {
            // Given
            SeckillOrder order = buildOrder(99);
            when(seckillOrderMapper.selectById(ORDER_ID)).thenReturn(order);

            // When
            SeckillOrderVO result = seckillService.getOrderDetail(ORDER_ID, USER_ID);

            // Then
            assertThat(result.getStatus()).isEqualTo("UNKNOWN");
            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("订单不存在时应返回 NOT_FOUND")
        void getOrderDetail_orderNotFound_returnsNotFound() {
            // Given
            when(seckillOrderMapper.selectById(ORDER_ID)).thenReturn(null);

            // When
            SeckillOrderVO result = seckillService.getOrderDetail(ORDER_ID, USER_ID);

            // Then
            assertThat(result.getStatus()).isEqualTo("NOT_FOUND");
            assertThat(result.getMessage()).isEqualTo("订单不存在");
        }

        @Test
        @DisplayName("用户无权查看他人订单时应返回 NOT_FOUND")
        void getOrderDetail_unauthorizedUser_returnsNotFound() {
            // Given
            SeckillOrder order = buildOrder(1);
            when(seckillOrderMapper.selectById(ORDER_ID)).thenReturn(order);

            // When - 使用不同的 userId
            SeckillOrderVO result = seckillService.getOrderDetail(ORDER_ID, 999L);

            // Then
            assertThat(result.getStatus()).isEqualTo("NOT_FOUND");
            assertThat(result.getMessage()).isEqualTo("订单不存在");
        }
    }
}
