package com.beibu.mall.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类实体类
 *
 * 对应数据库 t_category 表。
 * 分类支持无限层级：通过 parent_id 自关联实现树形结构。
 * 例如：
 *   活鲜（parent_id=0, level=1）
 *     ├── 鱼类（parent_id=活鲜ID, level=2）
 *     └── 虾类（parent_id=活鲜ID, level=2）
 */
@Data
@TableName("t_category")
public class Category {

    /** 分类ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父分类ID，0 表示顶级分类 */
    private Long parentId;

    /** 分类名称（如：活鲜、鱼类） */
    private String name;

    /** 层级深度：1=顶级，2=二级，3=三级... */
    private Integer level;

    /** 排序号（越小越靠前） */
    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}
