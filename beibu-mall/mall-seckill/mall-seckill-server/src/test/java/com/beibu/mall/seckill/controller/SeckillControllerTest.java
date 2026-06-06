package com.beibu.mall.seckill.controller;

import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.seckill.dto.SeckillRequestDTO;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.service.SeckillService;
import com.beibu.mall.seckill.vo.ActivityVO;
import com.beibu.mall.seckill.vo.SeckillOrderVO;
import com.beibu.mall.seckill.vo.SeckillResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillControllerTest {

    @Mock
    private SeckillService seckillService;

    @InjectMocks
    private SeckillController seckillController;

    @Test
    void doSeckill_success() {
        SeckillRequestDTO requestDTO = new SeckillRequestDTO();
        requestDTO.setActivityId(1L);
        Long userId = 100L;

        SeckillResultVO expectedResult = SeckillResultVO.builder()
                .success(true)
                .token("abc123def456abc123def456abc123de")
                .message("抢购成功")
                .activityId(1L)
                .build();

        when(seckillService.doSeckill(requestDTO, userId)).thenReturn(expectedResult);

        Result<SeckillResultVO> result = seckillController.doSeckill(requestDTO, userId);

        assertEquals(200, result.getCode());
        assertEquals(expectedResult, result.getData());
        assertTrue(result.getData().getSuccess());
        verify(seckillService).doSeckill(requestDTO, userId);
    }

    @Test
    void queryResult_success() {
        String token = "abc123def456abc123def456abc123de";
        SeckillOrderVO expectedResult = SeckillOrderVO.builder()
                .status("SUCCESS")
                .orderId(1001L)
                .productName("帝王蟹")
                .seckillPrice(new BigDecimal("99.99"))
                .build();

        when(seckillService.querySeckillResult(token)).thenReturn(expectedResult);

        Result<SeckillOrderVO> result = seckillController.queryResult(token);

        assertEquals(200, result.getCode());
        assertEquals(expectedResult, result.getData());
        assertEquals(1001L, result.getData().getOrderId());
        verify(seckillService).querySeckillResult(token);
    }

    @Test
    void getActivity_found() {
        Long activityId = 1L;
        SeckillActivity activity = new SeckillActivity();
        activity.setId(activityId);
        activity.setActivityName("帝王蟹限时秒杀");
        activity.setProductName("帝王蟹");
        activity.setProductId(200L);
        activity.setSeckillPrice(new BigDecimal("99.99"));
        activity.setOriginalPrice(new BigDecimal("299.99"));
        activity.setTotalStock(100);
        activity.setAvailableStock(50);
        activity.setStatus(0);

        when(seckillService.getActivity(activityId)).thenReturn(activity);

        Result<ActivityVO> result = seckillController.getActivity(activityId);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(activityId, result.getData().getId());
        assertEquals("帝王蟹限时秒杀", result.getData().getActivityName());
        assertEquals("帝王蟹", result.getData().getProductName());
        assertEquals(200L, result.getData().getProductId());
        assertEquals(new BigDecimal("99.99"), result.getData().getSeckillPrice());
        assertEquals(100, result.getData().getTotalStock());
        assertEquals(50, result.getData().getAvailableStock());
        verify(seckillService).getActivity(activityId);
    }

    @Test
    void getActivity_notFound() {
        Long activityId = 999L;
        when(seckillService.getActivity(activityId)).thenReturn(null);

        Result<ActivityVO> result = seckillController.getActivity(activityId);

        assertEquals(500, result.getCode());
        assertEquals("活动不存在", result.getMsg());
        assertNull(result.getData());
        verify(seckillService).getActivity(activityId);
    }

    @Test
    void warmUpStock_success() {
        Long activityId = 1L;
        SeckillActivity activity = new SeckillActivity();
        activity.setId(activityId);
        activity.setStatus(0);

        when(seckillService.getActivity(activityId)).thenReturn(activity);

        Result<String> result = seckillController.warmUpStock(activityId);

        assertEquals(200, result.getCode());
        assertEquals("库存预热完成", result.getData());
        verify(seckillService).warmUpStock(activityId);
    }

    @Test
    void warmUpStock_activityNotFound_stillCallsWarmUp() {
        Long activityId = 999L;
        when(seckillService.getActivity(activityId)).thenReturn(null);

        Result<String> result = seckillController.warmUpStock(activityId);

        assertEquals(200, result.getCode());
        assertEquals("库存预热完成", result.getData());
        verify(seckillService).warmUpStock(activityId);
    }

    @Test
    void warmUpStock_activeActivity_throwsBizException() {
        Long activityId = 1L;
        SeckillActivity activity = new SeckillActivity();
        activity.setId(activityId);
        activity.setStatus(1);

        when(seckillService.getActivity(activityId)).thenReturn(activity);

        BizException exception = assertThrows(BizException.class,
                () -> seckillController.warmUpStock(activityId));
        assertEquals("活动进行中，不允许手动预热", exception.getMessage());
        verify(seckillService, never()).warmUpStock(any());
    }

    @Test
    void getOrderDetail_success() {
        Long orderId = 1001L;
        Long userId = 100L;
        SeckillOrderVO expectedResult = SeckillOrderVO.builder()
                .status("SUCCESS")
                .orderId(orderId)
                .productName("帝王蟹")
                .seckillPrice(new BigDecimal("99.99"))
                .build();

        when(seckillService.getOrderDetail(orderId, userId)).thenReturn(expectedResult);

        Result<SeckillOrderVO> result = seckillController.getOrderDetail(orderId, userId);

        assertEquals(200, result.getCode());
        assertEquals(expectedResult, result.getData());
        assertEquals("SUCCESS", result.getData().getStatus());
        verify(seckillService).getOrderDetail(orderId, userId);
    }

    @Test
    void getOrderDetail_notFound() {
        Long orderId = 9999L;
        Long userId = 100L;
        SeckillOrderVO expectedResult = SeckillOrderVO.builder()
                .status("NOT_FOUND")
                .build();

        when(seckillService.getOrderDetail(orderId, userId)).thenReturn(expectedResult);

        Result<SeckillOrderVO> result = seckillController.getOrderDetail(orderId, userId);

        assertEquals(500, result.getCode());
        assertEquals("订单不存在", result.getMsg());
        verify(seckillService).getOrderDetail(orderId, userId);
    }
}
