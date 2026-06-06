package com.beibu.mall.seckill.config;

import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.service.SeckillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillSchedulerTest {

    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private SeckillService seckillService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private SeckillScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ---- autoWarmUpStock ----

    @Test
    void autoWarmUpStock_callsWarmUpForEachActivity() {
        SeckillActivity a1 = new SeckillActivity();
        a1.setId(1L);
        SeckillActivity a2 = new SeckillActivity();
        a2.setId(2L);

        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(a1, a2));

        scheduler.autoWarmUpStock();

        verify(seckillService).warmUpStock(1L);
        verify(seckillService).warmUpStock(2L);
    }

    @Test
    void autoWarmUpStock_noActivities_doesNothing() {
        when(seckillActivityMapper.selectList(any())).thenReturn(Collections.emptyList());

        scheduler.autoWarmUpStock();

        verify(seckillService, never()).warmUpStock(anyLong());
    }

    @Test
    void autoWarmUpStock_handlesExceptionGracefully() {
        SeckillActivity a1 = new SeckillActivity();
        a1.setId(1L);
        SeckillActivity a2 = new SeckillActivity();
        a2.setId(2L);

        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(a1, a2));
        doThrow(new RuntimeException("Redis down")).when(seckillService).warmUpStock(1L);

        // Should not propagate — exception is caught per-activity
        assertDoesNotThrow(() -> scheduler.autoWarmUpStock());

        // Second activity still processed
        verify(seckillService).warmUpStock(2L);
    }

    // ---- autoUpdateActivityStatus ----

    @Test
    void autoUpdateActivityStatus_updatesStartedAndEnded() {
        when(seckillActivityMapper.update(isNull(), any()))
                .thenReturn(2)  // 2 activities started
                .thenReturn(3); // 3 activities ended

        scheduler.autoUpdateActivityStatus();

        verify(seckillActivityMapper, times(2)).update(isNull(), any());
    }

    @Test
    void autoUpdateActivityStatus_noChanges() {
        when(seckillActivityMapper.update(isNull(), any()))
                .thenReturn(0)
                .thenReturn(0);

        scheduler.autoUpdateActivityStatus();

        verify(seckillActivityMapper, times(2)).update(isNull(), any());
    }

    // ---- syncStock ----

    @Test
    void syncStock_redisKeyMissing_warmsUpActivity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setAvailableStock(10);

        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(activity));
        when(redisTemplate.hasKey("seckill:stock:1")).thenReturn(false);

        scheduler.syncStock();

        verify(seckillService).warmUpStock(1L);
        // No direct stock set needed — warmUpStock handles it
        verify(valueOperations, never()).set(anyString(), any());
    }

    @Test
    void syncStock_redisStockHigher_syncsToDbStock() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setAvailableStock(5);

        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(activity));
        when(redisTemplate.hasKey("seckill:stock:1")).thenReturn(true);
        when(valueOperations.get("seckill:stock:1")).thenReturn(10);

        scheduler.syncStock();

        verify(valueOperations).set("seckill:stock:1", 5);
    }

    @Test
    void syncStock_redisStockLower_skips() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setAvailableStock(10);

        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(activity));
        when(redisTemplate.hasKey("seckill:stock:1")).thenReturn(true);
        when(valueOperations.get("seckill:stock:1")).thenReturn(3);

        scheduler.syncStock();

        // No sync — Redis is lower (possible in-flight MQ)
        verify(valueOperations, never()).set(anyString(), any());
    }

    @Test
    void syncStock_redisStockEqual_noAction() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setAvailableStock(5);

        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(activity));
        when(redisTemplate.hasKey("seckill:stock:1")).thenReturn(true);
        when(valueOperations.get("seckill:stock:1")).thenReturn(5);

        scheduler.syncStock();

        verify(valueOperations, never()).set(anyString(), any());
    }

    @Test
    void syncStock_exceptionCaughtPerActivity() {
        SeckillActivity a1 = new SeckillActivity();
        a1.setId(1L);
        SeckillActivity a2 = new SeckillActivity();
        a2.setId(2L);
        a2.setAvailableStock(5);

        when(seckillActivityMapper.selectList(any())).thenReturn(List.of(a1, a2));
        // First activity: hasKey throws
        when(redisTemplate.hasKey("seckill:stock:1")).thenThrow(new RuntimeException("Redis error"));
        // Second activity: normal flow
        when(redisTemplate.hasKey("seckill:stock:2")).thenReturn(true);
        when(valueOperations.get("seckill:stock:2")).thenReturn(3);

        assertDoesNotThrow(() -> scheduler.syncStock());

        // Second activity still processed
        verify(valueOperations, never()).set(anyString(), any()); // 3 < 5, skip
    }
}
