package com.beibu.mall.payment.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentMethod {

    ALIPAY(1, "支付宝"),
    WECHAT(2, "微信"),
    BANK_CARD(3, "银行卡");

    private final int code;
    private final String desc;

    public static PaymentMethod fromCode(int code) {
        for (PaymentMethod method : values()) {
            if (method.code == code) {
                return method;
            }
        }
        throw new IllegalArgumentException("无效的支付方式：" + code);
    }
}
