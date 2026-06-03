package com.beibu.mall.cart.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 购物车操作 DTO（Data Transfer Object，数据传输对象）
 *
 * DTO 的作用：
 * 接收前端传来的参数。为什么不用实体类直接接收？
 * 因为实体类里有 id、createTime、deleted 等字段，
 * 这些是后端自动生成的，不应该让前端传。
 * DTO 只包含前端需要传的字段，更安全、更清晰。
 *
 * 这个 DTO 同时用于"加入购物车"和"修改数量"两个接口：
 * - 加入购物车：skuId + quantity
 * - 修改数量：skuId + quantity
 */
@Data
public class CartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SKU ID（商品规格 ID）
     * 什么是 SKU？就是具体的商品规格，比如"大对虾/鲜活/500g"
     */
    @NotNull(message = "SKU ID 不能为空")
    private Long skuId;

    /**
     * 数量
     * @Min(1) 表示最小值为 1，不能加 0 个或负数个
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer quantity;
}
