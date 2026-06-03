package com.beibu.mall.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情 VO
 *
 * VO = View Object（视图对象）
 * 作用：返回给前端展示的数据结构
 *
 * 为什么需要 VO？
 * 1. 安全：不暴露数据库实体的敏感字段（如 deleted、version）
 * 2. 灵活：可以组合多个表的字段，前端需要什么就返回什么
 * 3. 稳定：数据库表结构变化时，VO 保持不变，前端不用改
 */
@Data
public class OrderVO {

    /** 订单ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人电话 */
    private String receiverPhone;

    /** 收货地址（格式化后的完整地址） */
    private String fullAddress;

    /**
     * 订单状态
     * 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消 5-已退款
     */
    private Integer status;

    /** 订单状态描述（前端直接展示，不需要再做转换） */
    private String statusDesc;

    /** 订单备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 商品列表 */
    private List<OrderItemVO> items;

    /**
     * 订单商品 VO
     */
    @Data
    public static class OrderItemVO {

        /** SKU ID */
        private Long skuId;

        /** 商品名称 */
        private String productName;

        /** SKU规格（如：鲜活/500g） */
        private String skuSpec;

        /** 商品图片URL */
        private String productImg;

        /** 单价 */
        private BigDecimal price;

        /** 购买数量 */
        private Integer quantity;

        /** 小计 */
        private BigDecimal subtotal;
    }
}
