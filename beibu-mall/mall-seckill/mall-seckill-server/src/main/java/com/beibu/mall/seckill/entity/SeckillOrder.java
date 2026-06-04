package com.beibu.mall.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体类
 *
 * 这个类和数据库的 seckill_order 表一一对应
 * 重点：表上有 uk_user_activity 唯一索引，保证一个用户在一个活动中只能抢一次
 */
@Data
@TableName("seckill_order")
public class SeckillOrder {

    /**
     * 订单ID（主键）
     * 使用雪花算法生成，全局唯一
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 活动ID */
    private Long activityId;

    /** 用户ID */
    private Long userId;

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 秒杀价格 */
    private BigDecimal seckillPrice;

    /**
     * 订单状态：0=待支付，1=已支付，2=已取消
     */
    private Integer orderStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0=未删除，1=已删除 */
    @TableLogic
    private Integer deleted;
}
