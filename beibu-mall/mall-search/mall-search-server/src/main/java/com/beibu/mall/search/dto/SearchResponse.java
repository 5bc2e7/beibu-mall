package com.beibu.mall.search.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 搜索结果响应
 *
 * 大白话：后端返回给前端的搜索结果
 */
@Data
public class SearchResponse {

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 商品列表
     */
    private List<ProductVO> products;

    /**
     * 搜索耗时（毫秒）
     */
    private Long took;

    /**
     * 商品 VO（搜索结果中的单个商品）
     */
    @Data
    public static class ProductVO {
        /** 商品 ID */
        private Long id;

        /** 分类 ID */
        private Long categoryId;

        /** 分类名称 */
        private String categoryName;

        /**
         * 商品名称（带高亮标签）
         * 如果用户搜"对虾"，返回的 name 会是"北部湾大<em>对虾</em>"
         * <em> 标签是 ES 自动加的，前端可以用 CSS 让它显示为红色
         */
        private String name;

        /** 产地 */
        private String origin;

        /** 是否活鲜 */
        private Integer isFresh;

        /** 最低价格 */
        private BigDecimal minPrice;

        /** 最高价格 */
        private BigDecimal maxPrice;

        /** 商品图片 */
        private String image;

        /** SKU 列表 */
        private List<SkuVO> skuList;
    }

    /**
     * SKU VO
     */
    @Data
    public static class SkuVO {
        /** SKU ID */
        private Long id;

        /** 规格描述 */
        private String spec;

        /** 价格 */
        private BigDecimal price;

        /** 库存 */
        private Integer stock;

        /** SKU 图片 */
        private String img;
    }
}
