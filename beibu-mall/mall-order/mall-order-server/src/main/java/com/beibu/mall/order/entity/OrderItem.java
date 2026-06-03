package com.beibu.mall.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单商品明细实体类
 *
 * 对应数据库 order_item 表。
 *
 * 为什么需要这个表？
 * 一个订单可以包含多个商品，这是典型的"一对多"关系。
 * 主表存订单基本信息，明细表存每个商品的信息。
 *
 * 设计要点：
 * - 商品信息冗余存储：下单时快照，避免商品修改影响历史订单
 * - price 和 subtotal 用 BigDecimal：避免浮点数精度问题
 */
@Data
@TableName("order_item")
public class OrderItem {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单ID（关联 order_info.id） */
    private Long orderId;

    /** 订单号（冗余，方便查询） */
    private String orderNo;

    /** SKU ID */
    private Long skuId;

    /** SPU ID */
    private Long spuId;

    /** 商品名称（下单时快照） */
    private String productName;

    /** SKU规格（如：鲜活/500g） */
    private String skuSpec;

    /** 商品图片URL */
    private String productImg;

    /** 单价（下单时的价格） */
    private BigDecimal price;

    /** 购买数量 */
    private Integer quantity;

    /** 小计 = price * quantity */
    private BigDecimal subtotal;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
