package com.beibu.mall.seckill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单查询 VO
 *
 * 前端用 token 查询秒杀结果时，返回这个对象
 * 包含订单的详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单状态
     * PROCESSING = 还在处理中（MQ还没消费完）
     * SUCCESS = 抢购成功，订单已生成
     * FAILED = 抢购失败（可能是重复抢购被数据库拒绝）
     */
    private String status;

    /**
     * 订单ID（只有成功时才有）
     */
    private Long orderId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;

    /**
     * 订单创建时间
     */
    private LocalDateTime createTime;

    /**
     * 提示信息
     */
    private String message;
}
