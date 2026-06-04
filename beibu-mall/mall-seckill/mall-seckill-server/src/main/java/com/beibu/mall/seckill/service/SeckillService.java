package com.beibu.mall.seckill.service;

import com.beibu.mall.seckill.dto.SeckillRequestDTO;
import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.vo.SeckillOrderVO;
import com.beibu.mall.seckill.vo.SeckillResultVO;

/**
 * 秒杀服务接口
 *
 * 接口 = 定义"做什么"，不关心"怎么做"
 * 好处：
 * 1. 解耦：Controller 只依赖接口，不依赖具体实现
 * 2. 可替换：可以有多种实现（比如测试时用 Mock 实现）
 * 3. 规范：一眼就能看出这个服务有哪些能力
 */
public interface SeckillService {

    /**
     * 库存预热
     *
     * 把数据库中的库存加载到 Redis
     * 在活动开始前调用，确保 Redis 中有库存数据
     *
     * @param activityId 活动ID
     */
    void warmUpStock(Long activityId);

    /**
     * 执行秒杀
     *
     * 这是秒杀的核心方法，流程：
     * 1. 执行 Lua 脚本（判断库存 + 判断是否重复 + 扣减库存）
     * 2. 如果成功，发送 MQ 消息（异步创建订单）
     * 3. 返回结果
     *
     * @param requestDTO 秒杀请求
     * @param userId 用户ID（从网关请求头获取）
     * @return 秒杀结果
     */
    SeckillResultVO doSeckill(SeckillRequestDTO requestDTO, Long userId);

    /**
     * 查询秒杀结果
     *
     * 前端用 token 查询最终结果
     *
     * @param token 查询令牌
     * @return 订单信息
     */
    SeckillOrderVO querySeckillResult(String token);

    /**
     * 查询活动信息
     *
     * @param activityId 活动ID
     * @return 活动信息
     */
    SeckillActivity getActivity(Long activityId);

    /**
     * 查询订单详情
     *
     * @param orderId 订单ID
     * @param userId 用户ID（用于权限校验）
     * @return 订单信息
     */
    SeckillOrderVO getOrderDetail(Long orderId, Long userId);
}
