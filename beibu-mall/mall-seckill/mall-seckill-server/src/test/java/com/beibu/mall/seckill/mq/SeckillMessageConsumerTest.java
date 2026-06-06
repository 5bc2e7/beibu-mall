package com.beibu.mall.seckill.mq;

import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.entity.SeckillOrder;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.mapper.SeckillOrderMapper;
import com.beibu.mall.seckill.vo.SeckillResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillMessageConsumerTest {

    @Mock
    private SeckillOrderMapper seckillOrderMapper;
    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private OrderTimeoutProducer orderTimeoutProducer;

    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private SeckillMessageConsumer consumer;

    @Captor
    private ArgumentCaptor<SeckillResultStatus> resultCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    private SeckillMessage createMessage(Long activityId, Long userId, String token) {
        SeckillMessage msg = new SeckillMessage();
        msg.setActivityId(activityId);
        msg.setUserId(userId);
        msg.setToken(token);
        return msg;
    }

    private SeckillActivity createActivity(Long id) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(id);
        activity.setProductId(200L);
        activity.setProductName("TestProduct");
        activity.setSeckillPrice(new BigDecimal("99.00"));
        activity.setAvailableStock(10);
        return activity;
    }

    @Test
    void onMessage_success() {
        SeckillMessage message = createMessage(1L, 100L, "token-abc");
        SeckillActivity activity = createActivity(1L);

        when(seckillActivityMapper.selectById(1L)).thenReturn(activity);
        when(seckillActivityMapper.update(isNull(SeckillActivity.class), any())).thenReturn(1);
        doReturn(1).when(seckillOrderMapper).insert((SeckillOrder) any());

        consumer.onMessage(message);

        // Stock decremented
        verify(seckillActivityMapper).update(isNull(SeckillActivity.class), any());
        // Order inserted
        verify(seckillOrderMapper).insert(any(SeckillOrder.class));
        // Result status set to SUCCESS after commit
        verify(valueOperations).set(
                eq("seckill:result:token-abc"), resultCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        SeckillResultStatus captured = resultCaptor.getValue();
        assertEquals("SUCCESS", captured.getStatus());
        assertEquals("抢购成功", captured.getMessage());
        // Timeout message sent
        verify(orderTimeoutProducer).sendTimeoutMessage(isNull(), eq(1L), eq(100L), eq(1800000L));
    }

    @Test
    void onMessage_activityNotFound() {
        SeckillMessage message = createMessage(1L, 100L, "token-abc");

        when(seckillActivityMapper.selectById(1L)).thenReturn(null);

        consumer.onMessage(message);

        // No stock decrement, no order insert
        verify(seckillActivityMapper, never()).update(any(), any());
        verify(seckillOrderMapper, never()).insert(any(SeckillOrder.class));
        // Result set to FAILED
        verify(valueOperations).set(
                eq("seckill:result:token-abc"), resultCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        assertEquals("FAILED", resultCaptor.getValue().getStatus());
        assertEquals("活动不存在", resultCaptor.getValue().getMessage());
    }

    @Test
    void onMessage_stockInsufficient() {
        SeckillMessage message = createMessage(1L, 100L, "token-abc");
        SeckillActivity activity = createActivity(1L);

        when(seckillActivityMapper.selectById(1L)).thenReturn(activity);
        when(seckillActivityMapper.update(isNull(SeckillActivity.class), any())).thenReturn(0);

        consumer.onMessage(message);

        // No order insert
        verify(seckillOrderMapper, never()).insert(any(SeckillOrder.class));
        // Result set to FAILED
        verify(valueOperations).set(
                eq("seckill:result:token-abc"), resultCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        assertEquals("FAILED", resultCaptor.getValue().getStatus());
        assertEquals("库存不足", resultCaptor.getValue().getMessage());
    }

    @Test
    void onMessage_duplicateKey() {
        SeckillMessage message = createMessage(1L, 100L, "token-abc");
        SeckillActivity activity = createActivity(1L);

        when(seckillActivityMapper.selectById(1L)).thenReturn(activity);
        // First call: stock decrement returns 1; second call: rollback returns 1
        when(seckillActivityMapper.update(isNull(SeckillActivity.class), any())).thenReturn(1).thenReturn(1);
        doThrow(new DuplicateKeyException("Duplicate entry for uk_user_activity"))
                .when(seckillOrderMapper).insert((SeckillOrder) any());

        consumer.onMessage(message);

        // DB stock rollback called (2nd update call)
        verify(seckillActivityMapper, times(2)).update(isNull(SeckillActivity.class), any());
        // Result set to FAILED directly (not after commit)
        verify(valueOperations).set(
                eq("seckill:result:token-abc"), resultCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        assertEquals("FAILED", resultCaptor.getValue().getStatus());
        assertEquals("您已经抢购过了", resultCaptor.getValue().getMessage());
        // No timeout message for duplicate orders
        verify(orderTimeoutProducer, never()).sendTimeoutMessage(any(), any(), any(), anyLong());
    }

    @Test
    void onMessage_nonRetryableException() {
        SeckillMessage message = createMessage(1L, 100L, "token-abc");
        SeckillActivity activity = createActivity(1L);

        when(seckillActivityMapper.selectById(1L)).thenReturn(activity);
        when(seckillActivityMapper.update(isNull(SeckillActivity.class), any())).thenReturn(1);
        // Generic RuntimeException (no retryable cause) during order insert
        doThrow(new RuntimeException("unexpected error"))
                .when(seckillOrderMapper).insert((SeckillOrder) any());

        // Should NOT propagate exception (non-retryable)
        consumer.onMessage(message);

        // Result set to FAILED directly
        verify(valueOperations).set(
                eq("seckill:result:token-abc"), resultCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));
        assertEquals("FAILED", resultCaptor.getValue().getStatus());
        assertEquals("系统异常，请稍后重试", resultCaptor.getValue().getMessage());
        // Redis stock rollback
        verify(valueOperations).increment("seckill:stock:1");
        verify(setOperations).remove("seckill:bought:1", "100");
        // No timeout message
        verify(orderTimeoutProducer, never()).sendTimeoutMessage(any(), any(), any(), anyLong());
    }
}
