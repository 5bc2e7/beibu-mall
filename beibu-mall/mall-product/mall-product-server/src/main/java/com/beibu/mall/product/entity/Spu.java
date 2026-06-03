package com.beibu.mall.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPU（Standard Product Unit）实体类
 *
 * 什么是 SPU？
 * SPU 是商品的"抽象定义"，代表一类商品。
 * 例如「北部湾大对虾」就是一个 SPU，它不包含具体规格（如 500g/1000g）。
 *
 * SPU 与 SKU 的关系：
 * - 一个 SPU 可以有多个 SKU（不同规格）
 * - SPU 定义商品的基本信息（名称、产地、是否活鲜等）
 * - SKU 定义商品的具体规格（价格、规格、库存等）
 */
@Data
@TableName("t_spu")
public class Spu {

    /** SPU ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属分类ID */
    private Long categoryId;

    /** 商品名称（如：北部湾大对虾） */
    private String name;

    /** 产地（如：广西北海） */
    private String origin;

    /**
     * 是否活鲜：1活鲜 0非活鲜
     * 活鲜商品的特点：保质期短、需要冷链运输、按重计价
     */
    private Integer isFresh;

    /**
     * 计价方式：0按件 1按重(克)
     * 按件：固定价格，如"一盒 ¥89"
     * 按重：按重量计价，如"每 500g ¥89"
     */
    private Integer priceType;

    /** 保质期（小时），活鲜商品保质期很短（如 24 小时） */
    private Integer shelfLife;

    /** 起售量（最小购买数量） */
    private Integer minBuy;

    /** 商品描述（支持富文本） */
    private String description;

    /** 状态：0下架 1上架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}
