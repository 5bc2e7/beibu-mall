package com.beibu.mall.search.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 搜索请求参数
 *
 * 大白话：前端搜索时传给后端的参数
 */
@Data
public class SearchRequest {

    /**
     * 搜索关键词（如"对虾"、"海鲜"）
     */
    private String keyword;

    /**
     * 分类 ID（筛选条件）
     */
    private Long categoryId;

    /**
     * 是否活鲜（筛选条件）：1活鲜 0非活鲜
     */
    private Integer isFresh;

    /**
     * 最低价格（价格区间筛选）
     */
    private BigDecimal minPrice;

    /**
     * 最高价格（价格区间筛选）
     */
    private BigDecimal maxPrice;

    /**
     * 排序字段
     * 可选值：price（价格）、createTime（时间）
     */
    private String sortField;

    /**
     * 排序方向
     * 可选值：asc（升序）、desc（降序）
     */
    private String sortOrder = "desc";

    /**
     * 页码（从 1 开始）
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;
}
