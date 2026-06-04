package com.beibu.mall.seckill.service.impl;

import cn.hutool.core.util.IdUtil;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.seckill.config.RedisKeyConstants;
import com.beibu.mall.seckill.dto.SeckillRequestDTO;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.entity.SeckillOrder;
import com.beibu.mall.seckill.enums.OrderStatus;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.mapper.SeckillOrderMapper;
import com.beibu.mall.seckill.mq.SeckillMessage;
import com.beibu.mall.seckill.mq.SeckillMessageProducer;
import com.beibu.mall.seckill.service.SeckillService;
import com.beibu.mall.seckill.vo.SeckillOrderVO;
import com.beibu.mall.seckill.vo.SeckillResultStatus;
import com.beibu.mall.seckill.vo.SeckillResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务实现类
 *
 * @Service：标记这是一个 Service 类，Spring 会自动创建它的实例并放入 IoC 容器
 * @Slf4j：Lombok 注解，自动生成 log 对象，用于打印日志
 * @RequiredArgsConstructor：Lombok 注解，自动生成包含 final 字段的构造方法
 *   这是 Spring 推荐的依赖注入方式（构造器注入）
 *
 * final 字段 = 必须在构造方法中赋值，赋值后不能修改
 * Spring 看到构造方法参数，会自动从 IoC 容器中找到对应的 Bean 注入进来
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    /** Jackson ObjectMapper（线程安全，复用避免重复创建） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * RedisTemplate：操作 Redis 的工具类
     * 我们在 RedisConfig 中配置了 JSON 序列化
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 秒杀 Lua 脚本对象
     * 在 RedisConfig 中通过 @Bean 加载
     */
    private final DefaultRedisScript<Long> seckillLuaScript;

    /**
     * 秒杀活动 Mapper
     * 操作 seckill_activity 表
     */
    private final SeckillActivityMapper seckillActivityMapper;

    /**
     * 秒杀订单 Mapper
     * 操作 seckill_order 表
     */
    private final SeckillOrderMapper seckillOrderMapper;

    /**
     * 秒杀消息生产者
     * 发送消息到 MQ
     */
    private final SeckillMessageProducer seckillMessageProducer;

    /**
     * 库存预热
     *
     * 把数据库中的库存加载到 Redis
     * 为什么？因为 Redis 在内存中，读写速度是微秒级，比 MySQL 快 100 倍
     * 高并发场景下，先查 Redis 判断库存，避免直接打到数据库
     *
     * @param activityId 活动ID
     */
    @Override
    public void warmUpStock(Long activityId) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException("活动不存在");
        }

        String stockKey = RedisKeyConstants.getStockKey(activityId);

        long ttlSeconds = java.time.Duration.between(LocalDateTime.now(), activity.getEndTime()).getSeconds() + 3600;
        ttlSeconds = Math.max(ttlSeconds, 60);

        redisTemplate.opsForValue().set(stockKey, activity.getAvailableStock(), ttlSeconds, TimeUnit.SECONDS);

        if (activity.getStatus() == 0) {
            String boughtKey = RedisKeyConstants.getBoughtKey(activityId);
            redisTemplate.delete(boughtKey);
        }

        log.info("库存预热完成，活动ID: {}, 库存: {}, TTL: {}s", activityId, activity.getAvailableStock(), ttlSeconds);
    }

    /**
     * 执行秒杀（核心方法）
     *
     * 流程：
     * 1. 执行 Lua 脚本（原子操作：判断库存 + 判断是否重复 + 扣减库存）
     * 2. 如果成功，发送 MQ 消息（异步创建订单）
     * 3. 返回结果
     *
     * @param requestDTO 秒杀请求
     * @param userId 用户ID（从网关请求头获取）
     * @return 秒杀结果
     */
    @Override
    public SeckillResultVO doSeckill(SeckillRequestDTO requestDTO, Long userId) {
        Long activityId = requestDTO.getActivityId();

        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException("活动不存在");
        }

        if (activity.getStatus() != 1) {
            throw new BizException("活动未在进行中");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BizException("活动尚未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BizException("活动已结束");
        }

        String stockKey = RedisKeyConstants.getStockKey(activityId);
        String boughtKey = RedisKeyConstants.getBoughtKey(activityId);

        Long result = redisTemplate.execute(
                seckillLuaScript,
                Arrays.asList(stockKey, boughtKey),
                userId.toString()
        );

        if (result == null) {
            throw new BizException("系统异常，请稍后重试");
        }

        if (result.longValue() == 1L) {
            return SeckillResultVO.builder()
                    .success(false)
                    .message("库存已售罄")
                    .activityId(activityId)
                    .build();
        }

        if (result.longValue() == 2L) {
            return SeckillResultVO.builder()
                    .success(false)
                    .message("您已经抢购过了，请勿重复下单")
                    .activityId(activityId)
                    .build();
        }

        String token = IdUtil.fastSimpleUUID();

        SeckillResultStatus processingStatus = new SeckillResultStatus();
        processingStatus.setStatus("PROCESSING");
        processingStatus.setMessage("订单处理中");

        String resultKey = RedisKeyConstants.getResultKey(token);
        redisTemplate.opsForValue().set(resultKey, processingStatus, RedisKeyConstants.RESULT_TTL_HOURS, TimeUnit.HOURS);

        try {
            sendSeckillMessage(activityId, userId, token);
        } catch (Exception e) {
            log.error("MQ发送失败，回滚Redis库存，活动ID: {}, 用户ID: {}", activityId, userId, e);
            rollbackRedisStock(activityId, userId);
            redisTemplate.delete(resultKey);
            throw new BizException("系统繁忙，请稍后重试");
        }

        log.info("秒杀成功，用户ID: {}, 活动ID: {}, token: {}", userId, activityId, token);

        return SeckillResultVO.builder()
                .success(true)
                .token(token)
                .message("抢购成功，请稍后查询结果")
                .activityId(activityId)
                .productName(activity.getProductName())
                .seckillPrice(activity.getSeckillPrice())
                .build();
    }

    /**
     * 发送秒杀 MQ 消息
     *
     * RocketMQ 是阿里巴巴开源的消息队列，类似于 Kafka
     * 消息队列的作用：
     * 1. 异步：发送消息后立刻返回，不用等数据库写完
     * 2. 解耦：秒杀服务不需要知道谁来处理订单
     * 3. 削峰：瞬间的 1000 个请求变成队列里的 1000 条消息，消费者慢慢处理
     *
     * @param activityId 活动ID
     * @param userId 用户ID
     * @param token 查询令牌
     */
    private void sendSeckillMessage(Long activityId, Long userId, String token) {
        SeckillMessage message = new SeckillMessage();
        message.setActivityId(activityId);
        message.setUserId(userId);
        message.setToken(token);
        message.setCreateTime(System.currentTimeMillis());

        seckillMessageProducer.sendMessage(message);
    }

    private void rollbackRedisStock(Long activityId, Long userId) {
        try {
            String stockKey = RedisKeyConstants.getStockKey(activityId);
            String boughtKey = RedisKeyConstants.getBoughtKey(activityId);

            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(boughtKey, userId.toString());

            log.info("Redis库存回滚完成，活动ID: {}, 用户ID: {}", activityId, userId);
        } catch (Exception e) {
            log.error("Redis库存回滚失败，活动ID: {}, 用户ID: {}", activityId, userId, e);
        }
    }

    /**
     * 查询秒杀结果
     *
     * 前端用 token 查询最终结果
     * 流程：
     * 1. 先查 Redis（MQ 还没处理完时，订单还在 Redis 中）
     * 2. 再查数据库（MQ 处理完后，订单已经入库）
     *
     * @param token 查询令牌
     * @return 订单信息
     */
    @Override
    public SeckillOrderVO querySeckillResult(String token) {
        String resultKey = RedisKeyConstants.getResultKey(token);
        Object cachedResult = redisTemplate.opsForValue().get(resultKey);

        if (cachedResult == null) {
            return SeckillOrderVO.builder()
                    .status("NOT_FOUND")
                    .message("查询结果不存在或已过期")
                    .build();
        }

        try {
            SeckillResultStatus resultStatus =
                    OBJECT_MAPPER.convertValue(cachedResult, SeckillResultStatus.class);

            if ("SUCCESS".equals(resultStatus.getStatus())) {
                return SeckillOrderVO.builder()
                        .status("SUCCESS")
                        .orderId(resultStatus.getOrderId())
                        .message(resultStatus.getMessage())
                        .build();
            } else if ("PROCESSING".equals(resultStatus.getStatus())) {
                return SeckillOrderVO.builder()
                        .status("PROCESSING")
                        .message("订单处理中，请稍后查询")
                        .build();
            } else {
                return SeckillOrderVO.builder()
                        .status("FAILED")
                        .message(resultStatus.getMessage())
                        .build();
            }
        } catch (Exception e) {
            log.error("解析秒杀结果失败", e);
            return SeckillOrderVO.builder()
                    .status("UNKNOWN")
                    .message("未知状态")
                    .build();
        }
    }

    /**
     * 查询活动信息
     *
     * @param activityId 活动ID
     * @return 活动信息
     */
    @Override
    public SeckillActivity getActivity(Long activityId) {
        return seckillActivityMapper.selectById(activityId);
    }

    @Override
    public SeckillOrderVO getOrderDetail(Long orderId, Long userId) {
        SeckillOrder order = seckillOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return SeckillOrderVO.builder()
                    .status("NOT_FOUND")
                    .message("订单不存在")
                    .build();
        }

        String status;
        switch (order.getOrderStatus()) {
            case 0 -> status = OrderStatus.PENDING_PAYMENT.name();
            case 1 -> status = OrderStatus.PAID.name();
            case 2 -> status = OrderStatus.CANCELLED.name();
            default -> status = "UNKNOWN";
        }

        return SeckillOrderVO.builder()
                .status(status)
                .orderId(order.getId())
                .productName(order.getProductName())
                .seckillPrice(order.getSeckillPrice())
                .createTime(order.getCreateTime())
                .message("订单详情")
                .build();
    }
}
