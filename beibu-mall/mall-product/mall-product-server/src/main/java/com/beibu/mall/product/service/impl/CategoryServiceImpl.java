package com.beibu.mall.product.service.impl;

import com.beibu.mall.product.api.dto.CategoryVO;
import com.beibu.mall.product.entity.Category;
import com.beibu.mall.product.mapper.CategoryMapper;
import com.beibu.mall.product.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类服务实现类
 *
 * @Service 标记这是一个 Service 类，Spring 会自动创建它的实例（Bean）
 * @RequiredArgsConstructor 自动生成包含 final 字段的构造方法，Spring 通过构造方法注入依赖
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    /**
     * 获取分类树
     *
     * 实现思路：
     * 1. 查询所有分类（一次查出来，避免 N+1 查询问题）
     * 2. 按 parentId 分组
     * 3. 递归构建树形结构
     */
    @Override
    public List<CategoryVO> getCategoryTree() {
        // 1. 查询所有启用的分类
        List<Category> allCategories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1)  // 只查启用的
                        .orderByAsc(Category::getSort) // 按排序号升序
        );

        // 2. 按 parentId 分组
        // Map<父分类ID, 该父分类下的所有子分类>
        Map<Long, List<Category>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        // 3. 从顶级分类（parentId=0）开始，递归构建树
        List<Category> topCategories = parentMap.getOrDefault(0L, new ArrayList<>());
        return buildTree(topCategories, parentMap);
    }

    /**
     * 递归构建分类树
     *
     * @param categories 当前层级的分类列表
     * @param parentMap  按 parentId 分组的映射
     * @return 树形结构的分类列表
     */
    private List<CategoryVO> buildTree(List<Category> categories, Map<Long, List<Category>> parentMap) {
        List<CategoryVO> voList = new ArrayList<>();

        for (Category category : categories) {
            CategoryVO vo = new CategoryVO();
            vo.setId(category.getId());
            vo.setParentId(category.getParentId());
            vo.setName(category.getName());
            vo.setLevel(category.getLevel());
            vo.setSort(category.getSort());
            vo.setStatus(category.getStatus());

            // 递归查找子分类
            List<Category> children = parentMap.getOrDefault(category.getId(), new ArrayList<>());
            if (!children.isEmpty()) {
                vo.setChildren(buildTree(children, parentMap));
            } else {
                vo.setChildren(new ArrayList<>());
            }

            voList.add(vo);
        }

        return voList;
    }
}
