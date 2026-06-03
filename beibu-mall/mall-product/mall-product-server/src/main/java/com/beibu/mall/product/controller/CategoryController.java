package com.beibu.mall.product.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.product.api.dto.CategoryVO;
import com.beibu.mall.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类控制器
 *
 * @RestController = @Controller + @ResponseBody
 * - @Controller：标记这是一个控制器类
 * - @ResponseBody：方法返回值自动转成 JSON
 *
 * @RequestMapping("/api/product/category")：统一 URL 前缀
 * 所有接口都以 /api/product/category 开头
 */
@RestController
@RequestMapping("/api/product/category")
@RequiredArgsConstructor
@Tag(name = "分类管理", description = "商品分类的增删改查")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 获取分类树
     *
     * 返回树形结构的分类列表，前端可以直接渲染成树形菜单。
     * 例如：
     *   活鲜
     *     ├── 鱼类
     *     └── 虾类
     *   冰鲜
     *     ├── 冰鲜鱼
     *     └── 冰鲜虾
     */
    @GetMapping("/tree")
    @Operation(summary = "获取分类树", description = "返回树形结构的分类列表")
    public Result<List<CategoryVO>> getCategoryTree() {
        List<CategoryVO> tree = categoryService.getCategoryTree();
        return Result.ok(tree);
    }
}
