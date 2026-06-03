package com.beibu.mall.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 下单请求 DTO
 *
 * DTO = Data Transfer Object（数据传输对象）
 * 作用：前端传给后端的数据结构
 *
 * 为什么需要 DTO？
 * 1. 参数校验：用注解自动校验参数合法性
 * 2. 安全：只接收需要的字段，避免恶意参数注入
 * 3. 清晰：一看就知道接口需要什么参数
 */
@Data
public class CreateOrderDTO {

    /**
     * 收货地址ID
     *
     * IMPORTANT-1: 当前 MVP 阶段，地址查询功能暂未实现，
     * 收货地址在 OrderServiceImpl 中硬编码为"待补充"。
     * 后续版本会对接用户服务，根据此 ID 查询真实收货地址。
     *
     * TODO: v2 版本实现地址查询功能
     */
    @NotNull(message = "地址ID不能为空")
    private Long addressId;

    /**
     * 商品列表
     * 用户可能一次买多个商品，所以是 List
     */
    @NotEmpty(message = "商品列表不能为空")
    @Valid  // 嵌套校验：List 里的每个 OrderItemDTO 也会被校验
    private List<OrderItemDTO> items;

    /** 订单备注（可选） */
    private String remark;

    /**
     * 单个商品信息 DTO
     *
     * 为什么单独一个类？
     * 因为 items 是嵌套在 CreateOrderDTO 里的，
     * 需要单独定义结构才能做参数校验。
     */
    @Data
    public static class OrderItemDTO {

        /** SKU ID（商品规格ID） */
        @NotNull(message = "SKU ID不能为空")
        private Long skuId;

        /**
         * 购买数量
         *
         * IMPORTANT-2: 添加上限校验，防止极端情况超出数据库 DECIMAL(10,2) 范围
         * 9999 是合理的单次购买上限，超过此数量需要分批下单
         */
        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量必须大于0")
        @Max(value = 9999, message = "单次购买数量不能超过9999")
        private Integer quantity;
    }
}
