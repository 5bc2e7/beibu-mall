package com.beibu.mall.seckill.mq;

import com.beibu.mall.seckill.config.RedisKeyConstants;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.entity.SeckillOrder;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.mapper.SeckillOrderMapper;
import com.beibu.mall.seckill.vo.SeckillResultStatus;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "seckill-topic", consumerGroup = "seckill-consumer-group")
public class SeckillMessageConsumer implements RocketMQListener<SeckillMessage> {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderTimeoutProducer orderTimeoutProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(SeckillMessage message) {
        Long activityId = message.getActivityId();
        Long userId = message.getUserId();
        String token = message.getToken();

        log.info("开始处理秒杀消息，活动ID: {}, 用户ID: {}, token: {}", activityId, userId, token);

        try {
            SeckillActivity activity = seckillActivityMapper.selectById(activityId);
            if (activity == null) {
                log.error("活动不存在，活动ID: {}", activityId);
                updateResultStatusAfterCommit(token, "FAILED", "活动不存在");
                return;
            }

            int affected = seckillActivityMapper.update(null,
                    new UpdateWrapper<SeckillActivity>()
                            .eq("id", activityId)
                            .gt("available_stock", 0)
                            .setSql("available_stock = available_stock - 1")
            );

            if (affected == 0) {
                log.warn("数据库库存不足，活动ID: {}", activityId);
                updateResultStatusAfterCommit(token, "FAILED", "库存不足");
                return;
            }

            SeckillOrder order = new SeckillOrder();
            order.setActivityId(activityId);
            order.setUserId(userId);
            order.setProductId(activity.getProductId());
            order.setProductName(activity.getProductName());
            order.setSeckillPrice(activity.getSeckillPrice());
            order.setOrderStatus(0);
            order.setCreateTime(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());
            order.setDeleted(0);

            seckillOrderMapper.insert(order);

            Long orderId = order.getId();
            updateResultStatusAfterCommit(token, "SUCCESS", "抢购成功", orderId);

            registerAfterCommit(() -> {
                orderTimeoutProducer.sendTimeoutMessage(orderId, activityId, userId, 30 * 60 * 1000);
                log.info("秒杀订单创建成功，订单ID: {}, 用户ID: {}, 活动ID: {}", orderId, userId, activityId);
            });

        } catch (DuplicateKeyException e) {
            log.warn("重复订单，用户已抢购过，活动ID: {}, 用户ID: {}", activityId, userId);
            rollbackDbStock(activityId);
            updateResultStatusDirectly(token, "FAILED", "您已经抢购过了");
            return;
        } catch (Exception e) {
            log.error("处理秒杀消息失败，活动ID: {}, 用户ID: {}", activityId, userId, e);
            updateResultStatusDirectly(token, "FAILED", "系统异常，请稍后重试");
            rollbackRedisStock(activityId, userId);
            if (isRetryableException(e)) {
                throw new RuntimeException("秒杀消息处理失败，触发重试", e);
            }
        }
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

    private void updateResultStatusAfterCommit(String token, String status, String message) {
        updateResultStatusAfterCommit(token, status, message, null);
    }

    private void updateResultStatusAfterCommit(String token, String status, String message, Long orderId) {
        Runnable action = () -> {
            String resultKey = RedisKeyConstants.getResultKey(token);
            SeckillResultStatus resultStatus = new SeckillResultStatus();
            resultStatus.setStatus(status);
            resultStatus.setMessage(message);
            resultStatus.setOrderId(orderId);
            redisTemplate.opsForValue().set(resultKey, resultStatus, RedisKeyConstants.RESULT_TTL_HOURS, TimeUnit.HOURS);
        };

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

    private void updateResultStatusDirectly(String token, String status, String message) {
        String resultKey = RedisKeyConstants.getResultKey(token);
        SeckillResultStatus resultStatus = new SeckillResultStatus();
        resultStatus.setStatus(status);
        resultStatus.setMessage(message);
        redisTemplate.opsForValue().set(resultKey, resultStatus, RedisKeyConstants.RESULT_TTL_HOURS, TimeUnit.HOURS);
    }

    private void rollbackRedisStock(Long activityId, Long userId) {
        try {
            String stockKey = RedisKeyConstants.getStockKey(activityId);
            String boughtKey = RedisKeyConstants.getBoughtKey(activityId);

            Long newStock = redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(boughtKey, userId.toString());

            log.info("Redis库存回滚完成，活动ID: {}, 用户ID: {}, 当前库存: {}", activityId, userId, newStock);
        } catch (Exception ex) {
            log.error("Redis库存回滚失败，活动ID: {}, 用户ID: {}", activityId, userId, ex);
        }
    }

    private void rollbackDbStock(Long activityId) {
        try {
            seckillActivityMapper.update(null,
                    new UpdateWrapper<SeckillActivity>()
                            .eq("id", activityId)
                            .setSql("available_stock = available_stock + 1")
            );
            log.info("DB库存回滚完成，活动ID: {}", activityId);
        } catch (Exception ex) {
            log.error("DB库存回滚失败，活动ID: {}", activityId, ex);
        }
    }

    private boolean isRetryableException(Exception e) {
        Throwable cause = e.getCause();
        if (cause instanceof java.sql.SQLTransientConnectionException
                || cause instanceof java.net.SocketTimeoutException
                || cause instanceof org.springframework.dao.TransientDataAccessException) {
            return true;
        }
        return false;
    }
}
