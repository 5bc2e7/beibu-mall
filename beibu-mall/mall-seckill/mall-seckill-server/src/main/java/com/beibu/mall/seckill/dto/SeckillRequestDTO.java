package com.beibu.mall.seckill.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀请求 DTO（Data Transfer Object，数据传输对象）
 *
 * DTO = 在服务之间、前端和后端之间传递数据的对象
 * 为什么不用实体类？因为实体类包含数据库字段（如 deleted、create_time）
 * 这些字段不应该暴露给前端
 *
 * @Data：Lombok 注解，自动生成 getter/setter
 */
@Data
public class SeckillRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 活动ID
     * @NotNull：Jakarta Validation 注解，表示这个字段不能为 null
     * 如果前端传了 null，Spring 会自动返回 400 错误
     */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;
}
