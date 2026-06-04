package com.beibu.mall.seckill.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.seckill.dto.SeckillRequestDTO;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.service.SeckillService;
import com.beibu.mall.seckill.vo.ActivityVO;
import com.beibu.mall.seckill.vo.SeckillOrderVO;
import com.beibu.mall.seckill.vo.SeckillResultVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
@Slf4j
public class SeckillController {

    private final SeckillService seckillService;

    @PostMapping("/do")
    @SentinelResource(value = "seckill", blockHandler = "handleSeckillBlock")
    public Result<SeckillResultVO> doSeckill(
            @Valid @RequestBody SeckillRequestDTO requestDTO,
            @RequestHeader("X-User-Id") @Min(value = 1, message = "用户ID不合法") Long userId) {
        log.info("收到秒杀请求，活动ID: {}, 用户ID: {}", requestDTO.getActivityId(), userId);

        SeckillResultVO result = seckillService.doSeckill(requestDTO, userId);
        return Result.ok(result);
    }

    public Result<SeckillResultVO> handleSeckillBlock(SeckillRequestDTO requestDTO, Long userId, BlockException ex) {
        log.warn("秒杀请求被限流，活动ID: {}, 用户ID: {}", requestDTO.getActivityId(), userId);
        return Result.fail(429, "活动太火爆，请稍后再试");
    }

    @GetMapping("/result/{token}")
    @SentinelResource(value = "seckill-query-result", blockHandler = "handleResultBlock")
    public Result<SeckillOrderVO> queryResult(
            @PathVariable @Pattern(regexp = "^[a-f0-9]{32}$", message = "token格式不正确") String token) {
        log.info("查询秒杀结果，token: {}", token);

        SeckillOrderVO result = seckillService.querySeckillResult(token);
        return Result.ok(result);
    }

    public Result<SeckillOrderVO> handleResultBlock(String token, BlockException ex) {
        log.warn("查询秒杀结果被限流，token: {}", token);
        return Result.fail(429, "查询太频繁，请稍后再试");
    }

    @GetMapping("/activity/{activityId}")
    @SentinelResource(value = "seckill-query-activity", blockHandler = "handleActivityBlock")
    public Result<ActivityVO> getActivity(@PathVariable Long activityId) {
        log.info("查询活动信息，活动ID: {}", activityId);

        SeckillActivity activity = seckillService.getActivity(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }

        ActivityVO vo = new ActivityVO();
        BeanUtils.copyProperties(activity, vo);
        return Result.ok(vo);
    }

    public Result<ActivityVO> handleActivityBlock(Long activityId, BlockException ex) {
        log.warn("查询活动信息被限流，活动ID: {}", activityId);
        return Result.fail(429, "查询太频繁，请稍后再试");
    }

    @PostMapping("/warmup/{activityId}")
    public Result<String> warmUpStock(@PathVariable Long activityId) {
        log.info("手动预热库存，活动ID: {}", activityId);

        SeckillActivity activity = seckillService.getActivity(activityId);
        if (activity != null && activity.getStatus() == 1) {
            throw new BizException("活动进行中，不允许手动预热");
        }

        seckillService.warmUpStock(activityId);
        return Result.ok("库存预热完成");
    }

    @GetMapping("/order/{orderId}")
    @SentinelResource(value = "seckill-query-order", blockHandler = "handleOrderBlock")
    public Result<SeckillOrderVO> getOrderDetail(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") @Min(value = 1, message = "用户ID不合法") Long userId) {
        log.info("查询订单详情，订单ID: {}, 用户ID: {}", orderId, userId);

        SeckillOrderVO order = seckillService.getOrderDetail(orderId, userId);
        if ("NOT_FOUND".equals(order.getStatus())) {
            return Result.fail("订单不存在");
        }
        return Result.ok(order);
    }

    public Result<SeckillOrderVO> handleOrderBlock(Long orderId, Long userId, BlockException ex) {
        log.warn("查询订单详情被限流，订单ID: {}, 用户ID: {}", orderId, userId);
        return Result.fail(429, "查询太频繁，请稍后再试");
    }
}
