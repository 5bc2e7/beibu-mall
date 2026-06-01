package com.beibu.mall.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果包装类
 *
 * 为什么需要这个类？
 * 前端（App/网页）调用后端接口时，需要一个统一的格式来判断请求是否成功。
 * 如果每个接口返回的格式不一样，前端代码会非常混乱。
 *
 * 统一格式示例：
 * {
 *   "code": 200,       // 业务状态码（200=成功，其他=失败）
 *   "msg": "success",  // 提示信息
 *   "data": { ... }    // 实际数据
 * }
 *
 * @param <T> data 的类型，可以是任何对象
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 返回数据 */
    private T data;

    /**
     * 成功（有数据）
     */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    /**
     * 成功（无数据，比如删除操作）
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 失败（自定义错误码和提示）
     */
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    /**
     * 失败（使用默认错误码 500）
     */
    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }
}
