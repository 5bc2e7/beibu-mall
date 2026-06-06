package com.beibu.mall.seckill.mq;

import com.beibu.mall.seckill.entity.SeckillOrder;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.mapper.SeckillOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutConsumerTest {

    @Mock
    private SeckillOrderMapper seckillOrderMapper;
    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private OrderTimeoutConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    private OrderTimeoutMessage createMessage(Long orderId, Long activityId, Long userId) {
        OrderTimeoutMessage msg = new OrderTimeoutMessage();
        msg.setOrderId(orderId);
        msg.setActivityId(activityId);
        msg.setUserId(userId);
        return msg;
    }

    @Test
    void onMessage_orderNotFound() {
        OrderTimeoutMessage message = createMessage(1001L, 1L, 100L);

        when(seckillOrderMapper.selectById(1001L)).thenReturn(null);

        consumer.onMessage(message);

        // No order update, no stock restore
        verify(seckillOrderMapper, never()).update(any(), any());
        verify(seckillActivityMapper, never()).update(any(), any());
    }

    @Test
    void onMessage_orderAlreadyProcessed() {
        OrderTimeoutMessage message = createMessage(1001L, 1L, 100L);

        SeckillOrder order = new SeckillOrder();
        order.setId(1001L);
        order.setOrderStatus(1); // already paid

        when(seckillOrderMapper.selectById(1001L)).thenReturn(order);

        consumer.onMessage(message);

        // No order update, no stock restore
        verify(seckillOrderMapper, never()).update(any(), any());
        verify(seckillActivityMapper, never()).update(any(), any());
    }

    @Test
    void onMessage_success() {
        OrderTimeoutMessage message = createMessage(1001L, 1L, 100L);

        SeckillOrder order = new SeckillOrder();
        order.setId(1001L);
        order.setOrderStatus(0); // pending payment

        when(seckillOrderMapper.selectById(1001L)).thenReturn(order);
        when(seckillOrderMapper.update(isNull(), any())).thenReturn(1);

        consumer.onMessage(message);

        // Order status updated to cancelled (2)
        verify(seckillOrderMapper).update(isNull(), any());
        // DB stock restored
        verify(seckillActivityMapper).update(isNull(), any());
        // Redis stock rollback (after commit, but no sync active so runs directly)
        verify(valueOperations).increment("seckill:stock:1");
        verify(setOperations).remove("seckill:bought:1", "100");
    }
}
