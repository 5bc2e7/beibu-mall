package com.beibu.mall.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存实体类
 *
 * 对应数据库 inventory_item 表。
 * @TableName("inventory_item") 告诉 MyBatis-Plus 这个类对应哪张表。
 *
 * 字段说明：
 * - availableStock：可用库存，当前可以卖给用户的数量
 * - lockedStock：预占库存，被订单锁定但还没真正扣减的数量
 * - totalStock：总库存 = 可用库存 + 预占库存
 * - version：版本号，乐观锁用，每次更新+1，防止并发超卖
 */
@Data
@TableName("inventory_item")
public class InventoryItem {

    /**
     * 主键ID
     * @TableId 表示这是主键
     * IdType.ASSIGN_ID = 雪花算法生成 ID（分布式环境下保证唯一）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 商品SKU ID
     * SKU = Stock Keeping Unit，库存量单位
     * 比如"红色XL码T恤"就是一个SKU，有独立的库存数量
     */
    private String skuId;

    /** 商品ID */
    private String productId;

    /** 商品名称（冗余字段，避免关联查询） */
    private String productName;

    /**
     * 可用库存
     * 当前可以卖给用户的数量 = 总库存 - 预占库存
     */
    private Integer availableStock;

    /**
     * 预占库存
     * 被订单锁定但还没真正扣减的数量
     * 比如下单时先预占1个，支付成功后再真正扣减
     */
    private Integer lockedStock;

    /**
     * 总库存
     * = 可用库存 + 预占库存
     * 这个字段是冗余的，方便查询，避免每次都要计算
     */
    private Integer totalStock;

    /**
     * 版本号
     * 每次更新时+1，用于记录变更次数。
     * 注意：防超卖靠的是原子 SQL 中的 available_stock >= quantity 条件，
     * 不是靠 version 检查。这里不加 @Version 注解，避免误导。
     */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}