package com.beibu.mall.payment.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付状态枚举
 *
 * 大白话：定义支付单可能的几种状态
 * - 待支付：用户下单后，还没付钱
 * - 支付成功：用户付了钱，我们收到了
 * - 支付失败：付钱过程中出了问题
 * - 已关闭：超时未支付，系统自动关闭
 */
@Getter
@AllArgsConstructor
public enum PaymentStatus {

    /** 待支付 */
    PENDING(0, "待支付"),

    /** 支付成功 */
    SUCCESS(1, "支付成功"),

    /** 支付失败 */
    FAILED(2, "支付失败"),

    /** 已关闭 */
    CLOSED(3, "已关闭");

    /** 状态码 */
    private final int code;

    /** 状态描述 */
    private final String desc;

    /**
     * 根据状态码获取枚举
     */
    public static PaymentStatus fromCode(int code) {
        for (PaymentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的支付状态码：" + code);
    }

    /**
     * 判断是否是终态（不能再变更）
     * 支付成功、支付失败、已关闭都是终态
     */
    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == CLOSED;
    }
}
