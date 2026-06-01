package com.beibu.mall.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 *
 * VO = View Object（视图对象）
 * 专门用于返回给前端的数据格式。
 * 为什么不直接返回实体类？因为实体类包含 password 字段，不能泄露给前端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** JWT 令牌 */
    private String token;
}
