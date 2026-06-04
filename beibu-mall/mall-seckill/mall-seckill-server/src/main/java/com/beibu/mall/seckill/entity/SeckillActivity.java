package com.beibu.mall.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体类
 *
 * @Data：Lombok 注解，自动生成 getter/setter/toString 等方法
 * @TableName：MyBatis-Plus 注解，指定对应的数据库表名
 *
 * 这个类和数据库的 seckill_activity 表一一对应
 * 每个字段对应表中的一列
 */
@Data
@TableName("seckill_activity")
public class SeckillActivity {

    /**
     * 活动ID（主键）
     * @TableId：标记这是主键
     * IdType.ASSIGN_ID：使用雪花算法生成ID
     * 雪花算法 = 一种分布式ID生成算法，保证全局唯一且有序
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 活动名称，比如"帝王蟹限时秒杀" */
    private String activityName;

    /** 商品ID（关联商品服务） */
    private Long productId;

    /** 商品名称（冗余存储，避免跨服务查询） */
    private String productName;

    /** 商品图片URL */
    private String productImage;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 总库存 */
    private Integer totalStock;

    /** 剩余可抢库存 */
    private Integer availableStock;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /**
     * 状态：0=未开始，1=进行中，2=已结束
     * @TableLogic：逻辑删除标记
     * 数据库中不真正删除数据，而是把 deleted 改为 1
     */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0=未删除，1=已删除 */
    @TableLogic
    private Integer deleted;
}
