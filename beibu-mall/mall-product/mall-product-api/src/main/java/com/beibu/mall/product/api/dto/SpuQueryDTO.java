package com.beibu.mall.product.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * SPU 查询条件 DTO
 *
 * DTO（Data Transfer Object）= 数据传输对象，用于接收前端传来的参数。
 * 为什么不用实体类？因为实体类包含数据库字段（如 deleted、createTime），
 * 这些字段不应该让前端传，也不应该暴露给前端。
 */
@Data
public class SpuQueryDTO {

    /** 分类ID（可选，按分类筛选） */
    private Long categoryId;

    /** 关键词（可选，按商品名称模糊搜索） */
    private String keyword;

    /** 状态（可选，按状态筛选：0下架 1上架） */
    private Integer status;

    /**
     * 页码（从 1 开始）
     * @Min(1) 表示最小值为 1，小于 1 会触发校验异常
     */
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    /**
     * 每页数量
     * @Max(100) 表示最大值为 100，防止前端传过大的值导致查询太慢
     */
    @Min(value = 1, message = "每页数量最小为 1")
    @Max(value = 100, message = "每页数量最大为 100")
    private Integer size = 10;
}
