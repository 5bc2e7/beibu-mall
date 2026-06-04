package com.beibu.mall.seckill.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单超时消息
 */
@Data
public class OrderTimeoutMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Long activityId;
    private Long userId;
}
