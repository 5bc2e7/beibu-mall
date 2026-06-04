package com.beibu.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.order.entity.OrderInfo;
import com.beibu.mall.order.entity.OrderItem;
import com.beibu.mall.order.entity.OrderStatus;
import com.beibu.mall.order.mapper.OrderInfoMapper;
import com.beibu.mall.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单事务服务
 *
 * 职责：处理订单相关的数据库事务操作
 * 为什么单独抽出来？
 * 解决 Spring 自调用（self-invocation）问题：
 * - 同一个类内调用 @Transactional 方法不会经过代理，事务不生效
 * - 通过注入不同的 Service，确保调用经过 Spring 代理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;

    /**
     * 事务内：更新订单状态为已支付
     *
     * @param orderNo 业务订单号
     * @param paymentTime 支付时间
     * @return 更新后的订单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderInfo updateOrderToPaid(String orderNo, LocalDateTime paymentTime) {
        OrderInfo orderInfo = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo)
        );
        if (orderInfo == null) {
            throw new BizException(40004, "订单不存在：" + orderNo);
        }

        Integer statusCode = orderInfo.getStatus();
        if (statusCode == null) {
            throw new BizException(40008, "订单状态异常");
        }
        OrderStatus currentStatus = OrderStatus.fromCode(statusCode);
        if (!currentStatus.canPay()) {
            log.warn("订单状态不允许支付，订单号：{}，当前状态：{}", orderNo, currentStatus);
            throw new BizException(40009, "订单状态不允许支付：" + currentStatus);
        }

        orderInfo.setStatus(OrderStatus.PAID.getCode());
        orderInfo.setPayTime(paymentTime);
        orderInfoMapper.updateById(orderInfo);

        log.info("订单状态已更新为已支付，订单号：{}", orderNo);
        return orderInfo;
    }

    /**
     * 事务内：更新订单状态为已取消
     *
     * @param orderNo 业务订单号
     * @param reason 取消原因
     * @return 更新后的订单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderInfo updateOrderToCancelled(String orderNo, String reason) {
        OrderInfo orderInfo = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo)
        );
        if (orderInfo == null) {
            throw new BizException(40004, "订单不存在：" + orderNo);
        }

        Integer statusCode = orderInfo.getStatus();
        if (statusCode == null) {
            throw new BizException(40008, "订单状态异常");
        }
        OrderStatus currentStatus = OrderStatus.fromCode(statusCode);
        if (!currentStatus.canCancel()) {
            log.warn("订单状态不允许取消，订单号：{}，当前状态：{}", orderNo, currentStatus);
            throw new BizException(40006, "订单状态不允许取消：" + currentStatus);
        }

        orderInfo.setStatus(OrderStatus.CANCELLED.getCode());
        orderInfo.setCancelTime(LocalDateTime.now());
        orderInfo.setCancelReason(reason);
        orderInfoMapper.updateById(orderInfo);

        log.info("订单状态已更新为取消，订单号：{}，原因：{}", orderNo, reason);
        return orderInfo;
    }

    /**
     * 查询订单明细
     *
     * @param orderNo 业务订单号
     * @return 订单明细列表
     */
    public List<OrderItem> getOrderItems(String orderNo) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo)
        );
    }
}
