package com.beibu.mall.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * SPU 详情 VO（商品详情页用）
 *
 * 包含 SPU 的完整信息 + 所有 SKU 列表。
 * 用于商品详情页面展示。
 */
@Data
public class SpuDetailVO {

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

    /** 保质期（小时） */
    private Integer shelfLife;

    /** 起售量 */
    private Integer minBuy;

    /** 商品描述 */
    private String description;

    /** 状态：0下架 1上架 */
    private Integer status;

    /** SKU 列表 */
    private List<SkuVO> skuList;
}
