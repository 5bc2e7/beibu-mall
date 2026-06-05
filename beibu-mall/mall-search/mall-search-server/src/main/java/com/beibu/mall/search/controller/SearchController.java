package com.beibu.mall.search.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.search.dto.SearchRequest;
import com.beibu.mall.search.dto.SearchResponse;
import com.beibu.mall.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 搜索控制器
 *
 * 大白话：处理前端的搜索请求
 */
@Tag(name = "搜索服务", description = "商品搜索、筛选、排序、高亮")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 搜索商品
     *
     * 大白话：前端输入关键词，后端返回匹配的商品列表
     *
     * @param keyword 搜索关键词（可选）
     * @param categoryId 分类 ID（可选，筛选条件）
     * @param isFresh 是否活鲜（可选，筛选条件）
     * @param minPrice 最低价格（可选，筛选条件）
     * @param maxPrice 最高价格（可选，筛选条件）
     * @param sortField 排序字段（可选：price、createTime）
     * @param sortOrder 排序方向（可选：asc、desc）
     * @param page 页码（默认 1）
     * @param size 每页大小（默认 10）
     * @return 搜索结果（带高亮）
     */
    @Operation(summary = "搜索商品", description = "支持关键词搜索、分类筛选、价格区间、排序、高亮")
    @GetMapping("/product")
    public Result<SearchResponse> searchProduct(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类 ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "是否活鲜：1是 0否") @RequestParam(required = false) Integer isFresh,
            @Parameter(description = "最低价格") @RequestParam(required = false) java.math.BigDecimal minPrice,
            @Parameter(description = "最高价格") @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @Parameter(description = "排序字段：price、createTime") @RequestParam(required = false) String sortField,
            @Parameter(description = "排序方向：asc、desc") @RequestParam(defaultValue = "desc") String sortOrder,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size
    ) {
        // 构建搜索请求
        SearchRequest request = new SearchRequest();
        request.setKeyword(keyword);
        request.setCategoryId(categoryId);
        request.setIsFresh(isFresh);
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setSortField(sortField);
        request.setSortOrder(sortOrder);
        request.setPage(page);
        request.setSize(size);

        // 执行搜索
        SearchResponse response = searchService.search(request);

        return Result.ok(response);
    }
}
