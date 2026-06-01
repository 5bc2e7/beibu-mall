package com.beibu.mall.user.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息 VO（返回给前端，不含密码）
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String phone;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDateTime createTime;
}
