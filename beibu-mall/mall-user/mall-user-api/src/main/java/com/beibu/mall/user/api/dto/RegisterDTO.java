package com.beibu.mall.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户注册请求参数
 *
 * DTO = Data Transfer Object（数据传输对象）
 * 专门用于接收前端传来的参数，不是数据库实体。
 * 为什么不用实体类接收？安全！实体类有 password、status 等字段，
 * 如果用实体接收，前端可以传 status=1 把自己设成管理员。
 */
@Data
public class RegisterDTO {

    /**
     * 用户名
     * @NotBlank = 不能为 null，且去除空格后不能为空字符串
     * message = 校验失败时的提示信息
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     * 前端传明文，后端加密后存库
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 手机号
     * @Pattern = 正则校验，必须符合手机号格式
     * ^1[3-9]\\d{9}$ = 以 1 开头，第二位 3-9，后面 9 位数字
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
