package com.beibu.mall.order.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态枚举
 *
 * 为什么用枚举而不是魔法数字（0,1,2,3...）？
 * 1. 可读性：OrderStatus.PENDING_PAYMENT 比 0 更易读
 * 2. 类型安全：编译期检查，不会传入无效值
 * 3. 易维护：修改状态只需改枚举，不用全局搜索替换
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    /** 待支付 */
    PENDING_PAYMENT(0, "待支付"),

    /** 已支付 */
    PAID(1, "已支付"),

    /** 已发货 */
    DELIVERED(2, "已发货"),

    /** 已完成 */
    COMPLETED(3, "已完成"),

    /** 已取消 */
    CANCELLED(4, "已取消"),

    /** 已退款 */
    REFUNDED(5, "已退款");

    /** 状态码 */
    private final int code;

    /** 状态描述 */
    private final String desc;

    /**
     * 根据状态码获取枚举
     *
     * @param code 状态码
     * @return 对应的枚举，如果不存在返回 null
     */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否可以取消
     * 只有待支付状态的订单才能取消
     */
    public boolean canCancel() {
        return this == PENDING_PAYMENT;
    }

    /**
     * 判断是否已终态（不能再变更）
     * 已完成、已取消、已退款都是终态
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == REFUNDED;
    }
}
