package com.beibu.mall.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SPU VO（商品列表用）
 *
 * 用于商品列表页面展示，包含 SPU 的基本信息。
 * 不包含 SKU 列表（商品详情才有）。
 */
@Data
public class SpuVO {

    /** SPU ID */
    private Long id;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 商品名称 */
    private String name;

    /** 产地 */
    private String origin;

    /** 是否活鲜：1活鲜 0非活鲜 */
    private Integer isFresh;

    /** 计价方式：0按件 1按重 */
    private Integer priceType;

    /** 起售量 */
    private Integer minBuy;

    /** 商品图片（取第一个 SKU 的图片） */
    private String image;

    /** 最低价格（用于列表展示） */
    private BigDecimal minPrice;

    /** 状态：0下架 1上架 */
    private Integer status;
}
