package com.beibu.mall.payment.service;

import com.beibu.mall.payment.dto.PaymentCallbackDTO;
import com.beibu.mall.payment.dto.PaymentCreateDTO;
import com.beibu.mall.payment.vo.PaymentVO;

/**
 * 支付服务接口
 *
 * 大白话：定义支付服务能做什么
 *
 * 为什么需要接口？
 * 1. 解耦：Controller 只依赖接口，不关心具体实现
 * 2. 可测试：单元测试时可以用 Mock 实现
 * 3. 可替换：以后换实现不用改 Controller
 */
public interface PaymentService {

    /**
     * 创建支付单
     *
     * @param dto 创建支付单请求
     * @return 支付单详情
     */
    PaymentVO createPayment(PaymentCreateDTO dto);

    /**
     * 处理支付回调（幂等）
     *
     * 大白话：支付宝/微信付完钱后调用这个接口
     * 幂等性保证：同一笔订单的重复回调只处理一次
     *
     * @param dto 回调请求
     * @return 支付单详情
     */
    PaymentVO handleCallback(PaymentCallbackDTO dto);

    /**
     * 查询支付单详情
     *
     * @param orderId 业务订单号
     * @return 支付单详情
     */
    PaymentVO getPaymentByOrderId(String orderId);
}
