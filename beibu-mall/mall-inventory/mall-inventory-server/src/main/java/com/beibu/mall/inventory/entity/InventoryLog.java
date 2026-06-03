package com.beibu.mall.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存变动流水实体类
 *
 * 对应数据库 inventory_log 表。
 * 记录每一次库存变动的明细，用于审计、排查问题、数据对账。
 *
 * 变动类型（changeType）：
 * - DEDUCT：直接扣减库存
 * - OCCUPY：预占库存（冻结）
 * - RELEASE：释放预占库存（解冻）
 * - CONFIRM：确认扣减（从预占转为真正扣减）
 */
@Data
@TableName("inventory_log")
public class InventoryLog {

    /**
     * 主键ID
     * @TableId 表示这是主键
     * IdType.ASSIGN_ID = 雪花算法生成 ID（分布式环境下保证唯一）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品SKU ID */
    private String skuId;

    /** 关联的订单ID（如果是订单扣减的话） */
    private String orderId;

    /**
     * 变动类型
     * DEDUCT-扣减，OCCUPY-预占，RELEASE-释放，CONFIRM-确认扣减
     */
    private String changeType;

    /**
     * 变动数量
     * 正数表示扣减/预占，负数表示释放
     */
    private Integer changeQuantity;

    /** 变动前库存 */
    private Integer beforeStock;

    /** 变动后库存 */
    private Integer afterStock;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}