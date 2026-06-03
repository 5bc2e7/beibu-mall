package com.beibu.mall.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU（Stock Keeping Unit）实体类
 *
 * 什么是 SKU？
 * SKU 是商品的"具体规格"，是库存管理的最小单位。
 * 例如「北部湾大对虾」这个 SPU 下有多个 SKU：
 *   - SKU1：鲜活/500g/¥89
 *   - SKU2：鲜活/1000g/¥168
 *   - SKU3：冰鲜/500g/¥69
 *
 * 每个 SKU 有独立的价格、库存、规格，是用户实际购买的商品。
 */
@Data
@TableName("t_sku")
public class Sku {

    /** SKU ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属 SPU ID */
    private Long spuId;

    /** 规格描述（如：鲜活/500g、冰鲜/1000g） */
    private String spec;

    /** 价格 */
    private BigDecimal price;

    /** 单位（件/g/kg） */
    private String unit;

    /** 库存数量 */
    private Integer stock;

    /** SKU 图片 */
    private String img;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}
