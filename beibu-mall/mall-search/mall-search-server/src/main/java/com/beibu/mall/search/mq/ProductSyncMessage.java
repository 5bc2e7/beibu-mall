package com.beibu.mall.search.mq;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品同步消息 DTO
 *
 * 大白话：商品服务通过 MQ 发送给搜索服务的数据格式
 *
 * 为什么要定义这个类？
 * 两个服务之间通过 MQ 通信，需要约定好"消息长什么样"
 * 就像寄快递要写清楚"收件人、地址、电话"
 */
@Data
public class ProductSyncMessage {

    /**
     * 操作类型：SAVE（新增/更新）、DELETE（删除）
     */
    private String action;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 分类 ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 产地
     */
    private String origin;

    /**
     * 是否活鲜：1活鲜 0非活鲜
     */
    private Integer isFresh;

    /**
     * 计价方式：0按件 1按重
     */
    private Integer priceType;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 状态：1上架 0下架
     */
    private Integer status;

    /**
     * SKU 列表
     */
    private List<SkuInfo> skuList;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * SKU 信息
     */
    @Data
    public static class SkuInfo {
        private Long id;
        private String spec;
        private BigDecimal price;
        private Integer stock;
        private String img;
    }
}
