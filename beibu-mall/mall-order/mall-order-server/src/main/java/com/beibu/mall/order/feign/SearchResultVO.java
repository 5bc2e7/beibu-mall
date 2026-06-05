package com.beibu.mall.order.feign;

import lombok.Data;

import java.io.Serializable;

/**
 * 搜索结果 VO（View Object）
 *
 * 为什么用 VO 而不是 Map？
 * - 类型安全：编译期检查字段名，避免拼写错误
 * - 代码提示：IDE 可以自动补全字段名
 * - 可读性：一看就知道有哪些字段
 *
 * VO vs DTO 的区别：
 * - DTO（Data Transfer Object）：服务间传输数据，通常和数据库字段对应
 * - VO（View Object）：返回给前端的数据，通常会做一些格式化
 *
 * 这里用 VO 是因为它代表搜索服务返回给前端的视图对象。
 */
@Data
public class SearchResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    private Long spuId;

    /** 商品名称 */
    private String spuName;

    /** 商品副标题 */
    private String subTitle;

    /** 商品图片 URL */
    private String imageUrl;

    /** 最低价（分） */
    private Integer minPrice;

    /** 销量 */
    private Integer salesCount;
}
