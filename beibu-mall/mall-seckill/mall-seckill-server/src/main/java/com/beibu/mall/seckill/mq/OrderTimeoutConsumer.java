package com.beibu.mall.seckill.mq;

import com.beibu.mall.seckill.config.RedisKeyConstants;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.entity.SeckillOrder;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.mapper.SeckillOrderMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "seckill-order-timeout-topic", consumerGroup = "seckill-order-timeout-consumer-group")
public class OrderTimeoutConsumer implements RocketMQListener<OrderTimeoutMessage> {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(OrderTimeoutMessage message) {
        Long orderId = message.getOrderId();
        Long activityId = message.getActivityId();
        Long userId = message.getUserId();

        log.info("检查订单超时，订单ID: {}, 用户ID: {}", orderId, userId);

        SeckillOrder order = seckillOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在，订单ID: {}", orderId);
            return;
        }

        if (order.getOrderStatus() != 0) {
            log.info("订单已处理，订单ID: {}, 状态: {}", orderId, order.getOrderStatus());
            return;
        }

        int affected = seckillOrderMapper.update(null,
                new UpdateWrapper<SeckillOrder>()
                        .eq("id", orderId)
                        .eq("order_status", 0)
                        .set("order_status", 2)
        );

        if (affected == 0) {
            log.info("订单已处理或不存在，跳过，订单ID: {}", orderId);
            return;
        }

        seckillActivityMapper.update(null,
                new UpdateWrapper<SeckillActivity>()
                        .eq("id", activityId)
                        .setSql("available_stock = available_stock + 1")
        );

        registerAfterCommit(() -> {
            rollbackRedisStock(activityId, userId);
            log.info("订单超时取消成功，订单ID: {}, 用户ID: {}", orderId, userId);
        });
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void rollbackRedisStock(Long activityId, Long userId) {
        try {
            String stockKey = RedisKeyConstants.getStockKey(activityId);
            String boughtKey = RedisKeyConstants.getBoughtKey(activityId);

            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(boughtKey, userId.toString());

            log.info("Redis 库存回滚成功，活动ID: {}, 用户ID: {}", activityId, userId);
        } catch (Exception e) {
            log.error("Redis 库存回滚失败，活动ID: {}, 用户ID: {}", activityId, userId, e);
        }
    }
}
