package com.beibu.mall.seckill.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀消息体
 *
 * MQ 消息需要一个"信封"来装数据
 * 这个类就是秒杀消息的"信封"
 *
 * @Data：自动生成 getter/setter
 */
@Data
public class SeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 查询令牌
     * 用户用这个 token 来查询结果
     */
    private String token;

    /**
     * 消息创建时间
     */
    private Long createTime;
}
