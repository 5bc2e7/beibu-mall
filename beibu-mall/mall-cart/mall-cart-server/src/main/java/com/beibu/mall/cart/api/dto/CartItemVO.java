package com.beibu.mall.cart.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车项 VO（View Object，视图对象）
 *
 * VO 的作用：
 * 返回给前端展示的数据。为什么不直接返回实体？
 * 1. 实体类的字段可能不适合前端展示（比如 deleted、createTime）
 * 2. VO 可以聚合多个来源的数据（比如从 Redis 拿数量，从商品服务拿名称和价格）
 *
 * 这个 VO 就是前端看到的"购物车里的一件商品"：
 * - skuId：商品规格 ID
 * - quantity：用户选了多少个
 * - productName：商品名称（来自商品服务）
 * - spec：规格描述，如"鲜活/500g"（来自商品服务）
 * - price：单价（来自商品服务）
 * - img：商品图片（来自商品服务）
 */
@Data
public class CartItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SKU ID */
    private Long skuId;

    /** 数量 */
    private Integer quantity;

    // ========== 以下字段来自商品服务（通过 Feign 调用获取） ==========

    /** 商品名称（如"北部湾大对虾"） */
    private String productName;

    /** 规格描述（如"鲜活/500g"） */
    private String spec;

    /** 单价 */
    private BigDecimal price;

    /** 商品图片 URL */
    private String img;
}
