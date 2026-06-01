package com.beibu.mall.common.exception;

import lombok.Getter;

/**
 * 自定义业务异常
 *
 * 为什么需要自定义异常？
 * Java 自带的异常（如 RuntimeException）只包含错误信息，没有业务错误码。
 * 前端需要根据错误码来展示不同的提示（比如"用户名已存在"vs"密码错误"）。
 *
 * 使用方式：
 * throw new BizException(40001, "用户名已存在");
 *
 * 错误码规划：
 * - 200：成功
 * - 400xx：客户端错误（参数校验、业务规则不满足）
 * - 500xx：服务端错误（系统异常、数据库异常）
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final Integer code;

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(500, message);
    }
}
