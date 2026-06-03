package com.beibu.mall.product.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 分类 VO（返回给前端）
 *
 * 树形结构：包含子分类列表 children。
 * 前端可以直接用这个对象渲染分类树。
 */
@Data
public class CategoryVO {

    /** 分类ID */
    private Long id;

    /** 父分类ID */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 层级深度 */
    private Integer level;

    /** 排序号 */
    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 子分类列表（递归结构） */
    private List<CategoryVO> children;
}
