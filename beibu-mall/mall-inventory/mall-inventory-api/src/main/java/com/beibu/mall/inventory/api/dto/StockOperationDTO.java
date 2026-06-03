package com.beibu.mall.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库存操作请求 DTO
 *
 * DTO = Data Transfer Object（数据传输对象）
 * 作用：在服务之间传递数据时使用的"快递包裹"
 *
 * 为什么不用实体类直接传？
 * 1. 安全：实体类可能包含敏感字段（如密码），DTO 只暴露需要的字段
 * 2. 灵活：DTO 可以组合多个实体的字段，不受数据库表结构限制
 * 3. 解耦：服务间通信不依赖数据库表结构
 */
@Data
public class StockOperationDTO {

    /**
     * 商品SKU ID
     * SKU = Stock Keeping Unit（库存量单位）
     * 比如"鲜活大虾/500g"就是一个 SKU
     */
    @NotBlank(message = "SKU ID不能为空")
    private String skuId;

    /**
     * 操作数量
     * 比如下单买了 2 件，quantity 就是 2
     */
    @NotNull(message = "操作数量不能为空")
    @Min(value = 1, message = "操作数量必须大于0")
    private Integer quantity;

    /**
     * 关联的订单ID
     * 用于记录库存流水，方便追溯"这个库存是被哪个订单占用的"
     */
    @NotBlank(message = "订单ID不能为空")
    private String orderId;
}
