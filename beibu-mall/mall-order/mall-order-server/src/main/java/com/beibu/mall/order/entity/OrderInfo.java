package com.beibu.mall.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体类
 *
 * 对应数据库 order_info 表。
 *
 * 设计要点：
 * 1. 收货地址信息冗余存储：避免用户修改地址后影响历史订单
 * 2. 金额用 BigDecimal：避免浮点数精度问题（0.1 + 0.2 != 0.3）
 * 3. 状态用数字：方便扩展，前端可以根据数字显示不同文案
 */
@Data
@TableName("order_info")
public class OrderInfo {

    /**
     * 主键ID（雪花算法生成）
     * 为什么不用数据库自增ID？
     * 1. 分布式环境下多个数据库可能生成相同ID
     * 2. 自增ID是连续的，容易被竞争对手猜到订单量
     * 3. 雪花算法生成的ID是趋势递增的，对索引友好
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号（雪花算法生成，全局唯一） */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 订单总金额（单位：元） */
    private BigDecimal totalAmount;

    /** 实付金额（总金额 - 优惠 + 运费） */
    private BigDecimal payAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人电话 */
    private String receiverPhone;

    /** 省 */
    private String receiverProvince;

    /** 市 */
    private String receiverCity;

    /** 区 */
    private String receiverDistrict;

    /** 详细地址 */
    private String receiverDetail;

    /**
     * 订单状态
     * 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消 5-已退款
     */
    private Integer status;

    /** 自动取消时间（下单后30分钟未支付自动取消） */
    private LocalDateTime autoCancelTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime deliverTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 取消原因 */
    private String cancelReason;

    /** 订单备注 */
    private String remark;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0未删除 1已删除 */
    @TableLogic
    private Integer deleted;
}
