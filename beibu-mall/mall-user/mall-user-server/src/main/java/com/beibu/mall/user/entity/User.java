package com.beibu.mall.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 *
 * 对应数据库 user 表。
 * @TableName("user") 告诉 MyBatis-Plus 这个类对应哪张表。
 *
 * MyBatis-Plus 会自动把 Java 对象映射到数据库表：
 * - 类名 User → 表名 user（驼峰转下划线）
 * - 属性 username → 字段 username
 * - 属性 createTime → 字段 create_time
 */
@Data
@TableName("user")
public class User {

    /**
     * 用户ID
     * @TableId 表示这是主键
     * IdType.ASSIGN_ID = 雪花算法生成 ID（分布式环境下保证唯一）
     * 为什么不用自增？因为微服务可能部署多个实例，自增 ID 会冲突
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户名（登录用） */
    private String username;

    /** 密码（BCrypt 加密后的密文） */
    private String password;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 状态：0-禁用 1-正常 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记
     * @TableLogic 告诉 MyBatis-Plus 这是逻辑删除字段
     * 调用 deleteById 时，不会真删，而是把 deleted 改为 1
     * 查询时自动过滤 deleted=1 的记录
     */
    @TableLogic
    private Integer deleted;
}
