package com.beibu.mall.payment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.payment.dto.PaymentCallbackDTO;
import com.beibu.mall.payment.dto.PaymentCreateDTO;
import com.beibu.mall.payment.entity.PaymentLog;
import com.beibu.mall.payment.entity.PaymentOrder;
import com.beibu.mall.payment.enums.PaymentMethod;
import com.beibu.mall.payment.enums.PaymentStatus;
import com.beibu.mall.payment.mapper.PaymentLogMapper;
import com.beibu.mall.payment.mapper.PaymentOrderMapper;
import com.beibu.mall.payment.mq.PaymentSuccessProducer;
import com.beibu.mall.payment.service.PaymentService;
import com.beibu.mall.payment.vo.PaymentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付服务实现类
 *
 * 大白话：这里写支付服务的具体业务逻辑
 *
 * 核心方法 handleCallback() 的幂等性实现：
 * 1. 先查数据库，看这笔订单的支付单是否存在
 * 2. 如果不存在 → 抛异常
 * 3. 如果已经是"支付成功" → 直接返回，不重复处理（这就是幂等！）
 * 4. 如果是"待支付" → 更新状态、发MQ消息、记日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentLogMapper paymentLogMapper;
    private final PaymentSuccessProducer paymentSuccessProducer;

    @Override
    @Transactional
    public PaymentVO createPayment(PaymentCreateDTO dto) {
        log.info("创建支付单，订单号：{}，金额：{}", dto.getOrderId(), dto.getAmount());

        // 检查是否已存在支付单（幂等性检查）
        PaymentOrder existing = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderId, dto.getOrderId())
        );
        if (existing != null) {
            log.warn("支付单已存在，订单号：{}，返回已有支付单", dto.getOrderId());
            return convertToVO(existing);
        }

        // 创建支付单
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setOrderId(dto.getOrderId());
        paymentOrder.setPaymentNo(generatePaymentNo());
        paymentOrder.setUserId(dto.getUserId());
        paymentOrder.setAmount(dto.getAmount());
        paymentOrder.setPaymentMethod(dto.getPaymentMethod());
        paymentOrder.setStatus(PaymentStatus.PENDING.getCode());

        paymentOrderMapper.insert(paymentOrder);

        // 记录操作日志
        saveLog(paymentOrder.getId(), paymentOrder.getOrderId(), "CREATE", null,
                PaymentStatus.PENDING.getCode(), "创建支付单");

        log.info("支付单创建成功，支付单号：{}", paymentOrder.getPaymentNo());
        return convertToVO(paymentOrder);
    }

    /**
     * 处理支付回调（核心方法！）
     *
     * 大白话：支付宝/微信付完钱后，会调用这个方法告诉我们"钱收到了"
     *
     * 幂等性实现的关键代码：
     * if (order.getStatus() == PaymentStatus.SUCCESS.getCode()) {
     *     return convertToVO(order);  // 已经处理过了，直接返回
     * }
     *
     * 这样即使支付宝发了10次回调，我们也只处理第一次。
     */
    @Override
    @Transactional
    public PaymentVO handleCallback(PaymentCallbackDTO dto) {
        log.info("收到支付回调，订单号：{}，状态：{}", dto.getOrderId(), dto.getCallbackStatus());

        if (!"SUCCESS".equals(dto.getCallbackStatus())) {
            throw new BizException(40021, "回调状态异常：" + dto.getCallbackStatus());
        }

        // ========== 1. 查询支付单 ==========
        PaymentOrder order = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderId, dto.getOrderId())
        );

        if (order == null) {
            throw new BizException(40020, "支付单不存在，订单号：" + dto.getOrderId());
        }

        // ========== 2. 幂等性检查（核心！） ==========
        if (order.getStatus() == PaymentStatus.SUCCESS.getCode()) {
            log.info("支付单已处理过（幂等），订单号：{}，直接返回", dto.getOrderId());
            return convertToVO(order);
        }

        // ========== 3. 终态检查 ==========
        if (order.getStatus() == PaymentStatus.CLOSED.getCode()) {
            throw new BizException(40022, "支付单已关闭，无法处理回调");
        }
        if (order.getStatus() == PaymentStatus.FAILED.getCode()) {
            throw new BizException(40023, "支付单已失败，无法处理回调");
        }

        // ========== 4. 更新支付单状态 ==========
        Integer beforeStatus = order.getStatus();

        order.setStatus(PaymentStatus.SUCCESS.getCode());
        order.setTradeNo(dto.getTradeNo());
        order.setPaymentTime(LocalDateTime.now());

        paymentOrderMapper.updateById(order);

        // ========== 5. 事务提交后发送 MQ 消息 ==========
        final PaymentOrder finalOrder = order;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paymentSuccessProducer.sendPaymentSuccess(finalOrder);
            }
        });

        // ========== 6. 记录操作日志 ==========
        saveLog(order.getId(), order.getOrderId(), "CALLBACK", beforeStatus,
                PaymentStatus.SUCCESS.getCode(), "支付成功，交易号：" + dto.getTradeNo());

        log.info("支付回调处理完成，订单号：{}", dto.getOrderId());
        return convertToVO(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentVO getPaymentByOrderId(String orderId) {
        PaymentOrder order = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderId, orderId)
        );

        if (order == null) {
            throw new BizException(40020, "支付单不存在，订单号：" + orderId);
        }

        return convertToVO(order);
    }

    /**
     * 保存操作日志
     */
    private void saveLog(Long paymentId, String orderId, String operation,
                         Integer beforeStatus, Integer afterStatus, String remark) {
        PaymentLog logEntity = new PaymentLog();
        logEntity.setPaymentId(paymentId);
        logEntity.setOrderId(orderId);
        logEntity.setOperation(operation);
        logEntity.setBeforeStatus(beforeStatus);
        logEntity.setAfterStatus(afterStatus);
        logEntity.setRemark(remark);
        // createTime 由 MyMetaObjectHandler 自动填充

        paymentLogMapper.insert(logEntity);
    }

    private String generatePaymentNo() {
        return "PAY" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * 实体转VO
     *
     * 大白话：把数据库实体转换成返回给前端的格式
     * 为什么要转换？因为实体类包含 deleted 等敏感字段，不应该暴露给前端
     */
    private PaymentVO convertToVO(PaymentOrder order) {
        PaymentVO vo = new PaymentVO();
        BeanUtil.copyProperties(order, vo);

        // 设置状态描述
        PaymentStatus status = PaymentStatus.fromCode(order.getStatus());
        vo.setStatusDesc(status.getDesc());

        // 设置支付方式描述
        vo.setPaymentMethodDesc(getPaymentMethodDesc(order.getPaymentMethod()));

        return vo;
    }

    /**
     * 获取支付方式描述
     */
    private String getPaymentMethodDesc(Integer method) {
        if (method == null) return "未知";
        try {
            return PaymentMethod.fromCode(method).getDesc();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }
}
