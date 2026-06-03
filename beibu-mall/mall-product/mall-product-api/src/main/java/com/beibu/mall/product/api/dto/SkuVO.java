package com.beibu.mall.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU VO（返回给前端）
 *
 * 用于展示 SKU 的详细信息。
 */
@Data
public class SkuVO {

    /** SKU ID */
    private Long id;

    /** 所属 SPU ID */
    private Long spuId;

    /** 规格描述（如：鲜活/500g） */
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
}
