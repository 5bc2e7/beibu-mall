package com.beibu.mall.search.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.DateFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品搜索文档（存储在 Elasticsearch 中）
 *
 * 大白话：这个类定义了"在 ES 里存什么样的商品数据"
 *
 * 什么是 Document？
 * 在 ES 里，一条数据叫做一个"文档"（Document），类似于数据库里的一行记录。
 * 这个类就是告诉 Spring Data ES："帮我把这种格式的数据存到 ES 里"
 *
 * @Document 注解说明：
 * - indexName = "product"：索引名称，类似于数据库的表名
 * - 每个字段的 @Field 注解定义了字段的类型和分词方式
 */
@Data
@Document(indexName = "product")
public class ProductDoc {

    /**
     * 商品 ID（SPU ID）
     * @Id：告诉 ES 这是主键
     */
    @Id
    private Long id;

    /**
     * 分类 ID
     * @Field(type = FieldType.Long)：存为长整数，用于精确匹配筛选
     */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /**
     * 分类名称
     * @Field(type = FieldType.Keyword)：不分词，整体存储
     * 用途：分类筛选时精确匹配
     */
    @Field(type = FieldType.Keyword)
    private String categoryName;

    /**
     * 商品名称
     * @Field(type = FieldType.Text)：使用标准分词器
     */
    @Field(type = FieldType.Text)
    private String name;

    /**
     * 产地
     * 也用标准分词，方便搜"广西"、"北海"等
     */
    @Field(type = FieldType.Text)
    private String origin;

    /**
     * 是否活鲜：1活鲜 0非活鲜
     * 用于筛选
     */
    @Field(type = FieldType.Integer)
    private Integer isFresh;

    /**
     * 计价方式：0按件 1按重
     * 用于筛选
     */
    @Field(type = FieldType.Integer)
    private Integer priceType;

    /**
     * 最低价格
     * 用于价格区间筛选和排序
     */
    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    /**
     * 最高价格
     * 用于价格区间筛选
     */
    @Field(type = FieldType.Double)
    private BigDecimal maxPrice;

    /**
     * 商品图片
     * Keyword 类型，不分词
     */
    @Field(type = FieldType.Keyword)
    private String image;

    /**
     * 商品描述
     * 也做标准分词，支持描述内容搜索
     */
    @Field(type = FieldType.Text)
    private String description;

    /**
     * 状态：1上架 0下架
     * 只有上架的商品才应该出现在搜索结果中
     */
    @Field(type = FieldType.Integer)
    private Integer status;

    /**
     * SKU 列表（规格信息）
     * 嵌套对象类型，一个商品有多个规格
     */
    @Field(type = FieldType.Nested)
    private List<SkuInfo> skuList;

    /**
     * 创建时间
     * 用于按时间排序
     */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * SKU 信息（嵌套在商品文档中）
     *
     * 大白话：一个商品有多个规格（比如 500g、1000g），每个规格就是一个 SKU
     */
    @Data
    public static class SkuInfo {
        /** SKU ID */
        @Field(type = FieldType.Long)
        private Long id;

        /** 规格描述（如：鲜活/500g） */
        @Field(type = FieldType.Keyword)
        private String spec;

        /** 价格 */
        @Field(type = FieldType.Double)
        private BigDecimal price;

        /** 库存 */
        @Field(type = FieldType.Integer)
        private Integer stock;

        /** SKU 图片 */
        @Field(type = FieldType.Keyword)
        private String img;
    }
}
