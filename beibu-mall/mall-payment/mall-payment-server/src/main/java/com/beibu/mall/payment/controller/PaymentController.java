package com.beibu.mall.payment.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.payment.dto.PaymentCallbackDTO;
import com.beibu.mall.payment.dto.PaymentCreateDTO;
import com.beibu.mall.payment.service.PaymentService;
import com.beibu.mall.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 支付控制器
 *
 * 大白话：这是支付服务的"门面"，前端/其他服务通过 HTTP 请求调用这些接口
 *
 * 三层架构：
 * Controller（接口层）→ Service（业务层）→ Mapper（数据层）
 *
 * Controller 的职责：
 * 1. 接收请求参数
 * 2. 参数校验（@Valid 自动校验）
 * 3. 调用 Service 处理业务
 * 4. 返回统一格式的结果（Result 包装）
 *
 * 注意：Controller 不应该有业务逻辑！业务逻辑放在 Service 里。
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 创建支付单
     *
     * 大白话：用户下单后，调用这个接口创建支付单
     * 然后跳转到支付宝/微信收银台付钱
     *
     * @param dto 创建支付单请求
     * @return 支付单详情
     */
    @PostMapping("/create")
    public Result<PaymentVO> createPayment(@Valid @RequestBody PaymentCreateDTO dto) {
        PaymentVO payment = paymentService.createPayment(dto);
        return Result.ok(payment);
    }

    /**
     * 支付回调接口（幂等）
     *
     * 大白话：支付宝/微信付完钱后，会调用这个接口告诉我们"钱收到了"
     *
     * 为什么这个接口必须幂等？
     * 因为支付宝/微信可能因为网络问题重复发送回调通知，
     * 如果不幂等，可能导致重复发货、重复扣款等问题。
     *
     * @param dto 回调请求
     * @return 支付单详情
     */
    @PostMapping("/callback")
    public Result<PaymentVO> handleCallback(@Valid @RequestBody PaymentCallbackDTO dto) {
        PaymentVO payment = paymentService.handleCallback(dto);
        return Result.ok(payment);
    }

    /**
     * 查询支付单详情
     *
     * @param orderId 业务订单号
     * @return 支付单详情
     */
    @GetMapping("/order/{orderId}")
    public Result<PaymentVO> getPaymentByOrderId(@PathVariable String orderId) {
        PaymentVO payment = paymentService.getPaymentByOrderId(orderId);
        return Result.ok(payment);
    }
}
