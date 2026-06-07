package com.beibu.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.product.api.dto.CategoryVO;
import com.beibu.mall.product.entity.Category;
import com.beibu.mall.product.mapper.CategoryMapper;
import com.beibu.mall.product.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CategoryService 单元测试
 *
 * 什么是单元测试？
 * - 测试一个类的方法是否正确，不依赖数据库、网络等外部资源
 * - 用 Mock（模拟）代替真实的 Mapper，只测 Service 逻辑
 *
 * 什么是 Mockito？
 * - 一个 Mock 框架，可以模拟任何对象的行为
 * - 比如：模拟 CategoryMapper.selectList() 返回指定值，而不是真的查数据库
 *
 * @ExtendWith(MockitoExtension.class)：启用 Mockito 注解
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")  // LambdaQueryWrapper 是泛型，Mockito.any() 无法推断类型
class CategoryServiceTest {

    @Mock  // 创建一个 Mock 对象
    private CategoryMapper categoryMapper;

    @InjectMocks  // 把 Mock 对象注入到 CategoryServiceImpl 中
    private CategoryServiceImpl categoryService;

    private Category topCategory;
    private Category subCategory;

    @BeforeEach  // 每个测试方法执行前都会调用
    void setUp() {
        // 准备测试数据
        topCategory = new Category();
        topCategory.setId(1L);
        topCategory.setParentId(0L);
        topCategory.setName("活鲜");
        topCategory.setLevel(1);
        topCategory.setSort(1);
        topCategory.setStatus(1);

        subCategory = new Category();
        subCategory.setId(5L);
        subCategory.setParentId(1L);
        subCategory.setName("鱼类");
        subCategory.setLevel(2);
        subCategory.setSort(1);
        subCategory.setStatus(1);
    }

    @Test
    @DisplayName("获取分类树 - 成功")
    void getCategoryTree_success() {
        // given：准备条件
        // 模拟数据库返回所有分类
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(topCategory, subCategory));

        // when：执行被测试的方法
        List<CategoryVO> tree = categoryService.getCategoryTree();

        // then：验证结果
        assertNotNull(tree);
        assertEquals(1, tree.size());  // 只有一个顶级分类
        assertEquals("活鲜", tree.get(0).getName());
        assertNotNull(tree.get(0).getChildren());
        assertEquals(1, tree.get(0).getChildren().size());  // 有一个子分类
        assertEquals("鱼类", tree.get(0).getChildren().get(0).getName());
    }

    @Test
    @DisplayName("获取分类树 - 空列表")
    void getCategoryTree_empty() {
        // given：模拟数据库返回空列表
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList());

        // when
        List<CategoryVO> tree = categoryService.getCategoryTree();

        // then
        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }
}
