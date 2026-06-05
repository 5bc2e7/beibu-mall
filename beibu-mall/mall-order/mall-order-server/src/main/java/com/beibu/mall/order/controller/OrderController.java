package com.beibu.mall.order.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.order.api.dto.CreateOrderDTO;
import com.beibu.mall.order.api.dto.OrderVO;
import com.beibu.mall.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器
 *
 * Controller = 接口层（API 层）
 * 作用：接收前端请求，调用 Service 处理，返回结果
 *
 * 三层架构：
 * Controller（接口层）→ Service（业务层）→ Mapper（数据层）
 *
 * Controller 的职责：
 * 1. 接收请求参数
 * 2. 参数校验（@Valid 注解自动校验）
 * 3. 调用 Service 处理业务
 * 4. 返回统一格式的结果（Result 包装）
 *
 * 注意：Controller 不应该有业务逻辑！业务逻辑放在 Service 里。
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单
     *
     * @param createOrderDTO 下单请求参数（@Valid 自动校验参数）
     * @param userId 用户ID（从请求头获取，实际项目中从 Token 解析）
     * @return 订单详情
     */
    @PostMapping
    @SentinelResource(value = "createOrder", blockHandler = "createOrderBlockHandler")
    public Result<OrderVO> createOrder(
            @Valid @RequestBody CreateOrderDTO createOrderDTO,
            @RequestHeader("X-User-Id") Long userId) {
        OrderVO order = orderService.createOrder(createOrderDTO, userId);
        return Result.ok(order);
    }

    /**
     * 下单接口的限流处理方法
     *
     * 当请求被 Sentinel 拦截时（比如 QPS 超过 50），会调用这个方法
     * 返回友好提示，而不是直接报错
     *
     * 注意：blockHandler 方法的参数必须和原方法一样，最后加一个 BlockException 参数
     *
     * @param ex Sentinel 抛出的异常，包含限流规则信息
     * @return 友好的错误提示
     */
    public Result<OrderVO> createOrderBlockHandler(
            CreateOrderDTO createOrderDTO,
            Long userId,
            BlockException ex) {
        log.warn("下单接口被限流，规则: {}", ex.getRule());
        return Result.fail(429, "下单人数太多啦，请稍后再试~");
    }

    /**
     * 查询我的订单（分页）
     *
     * @param userId 用户ID
     * @param page 页码（默认1）
     * @param size 每页数量（默认10）
     * @return 分页结果
     */
    @GetMapping("/my")
    public Result<Page<OrderVO>> listMyOrders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Page<OrderVO> result = orderService.listMyOrders(userId, page, size);
        return Result.ok(result);
    }

    /**
     * 查询订单详情
     *
     * @param orderId 订单ID
     * @param userId 用户ID
     * @return 订单详情
     */
    @GetMapping("/{orderId}")
    @SentinelResource(value = "queryOrder", blockHandler = "queryOrderBlockHandler")
    public Result<OrderVO> getOrderDetail(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId) {
        OrderVO order = orderService.getOrderDetail(orderId, userId);
        return Result.ok(order);
    }

    /**
     * 查询订单接口的限流处理方法
     */
    public Result<OrderVO> queryOrderBlockHandler(
            Long orderId,
            Long userId,
            BlockException ex) {
        log.warn("查询订单接口被限流，规则: {}", ex.getRule());
        return Result.fail(429, "查询太频繁，请稍后再试");
    }

    /**
     * 取消订单
     *
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param reason 取消原因（可选）
     * @return 操作结果
     */
    @PostMapping("/{orderId}/cancel")
    @SentinelResource(value = "cancelOrder", blockHandler = "cancelOrderBlockHandler")
    public Result<Void> cancelOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String reason) {
        orderService.cancelOrder(orderId, userId, reason);
        return Result.ok();
    }

    public Result<Void> cancelOrderBlockHandler(
            Long orderId,
            Long userId,
            String reason,
            BlockException ex) {
        log.warn("取消订单接口被限流，规则: {}", ex.getRule());
        return Result.fail(429, "操作太频繁，请稍后再试");
    }
}
