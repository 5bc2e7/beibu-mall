package com.beibu.mall.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SPU 保存 DTO（添加/修改商品用）
 *
 * 前端提交商品信息时使用这个对象。
 * @NotBlank = 不能为 null 且不能为 ""（空字符串）
 * @NotNull = 不能为 null（但可以为 ""）
 */
@Data
public class SpuSaveDTO {

    /** SPU ID（修改时必传，添加时不传） */
    private Long id;

    /** 分类ID */
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    /** 商品名称 */
    @NotBlank(message = "商品名称不能为空")
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
}
