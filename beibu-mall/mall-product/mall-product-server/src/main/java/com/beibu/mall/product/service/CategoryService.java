package com.beibu.mall.product.service;

import com.beibu.mall.product.api.dto.CategoryVO;

import java.util.List;

/**
 * 分类服务接口
 *
 * 定义分类相关的业务方法。
 * 接口的好处：解耦实现，方便测试和替换。
 */
public interface CategoryService {

    /**
     * 获取分类树
     *
     * 返回树形结构的分类列表，前端可以直接用这个渲染分类树。
     * 例如：
     *   活鲜
     *     ├── 鱼类
     *     └── 虾类
     *   冰鲜
     *     ├── 冰鲜鱼
     *     └── 冰鲜虾
     */
    List<CategoryVO> getCategoryTree();
}
