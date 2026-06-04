package com.beibu.mall.seckill.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING_PAYMENT(0, "待支付"),
    PAID(1, "已支付"),
    CANCELLED(2, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
