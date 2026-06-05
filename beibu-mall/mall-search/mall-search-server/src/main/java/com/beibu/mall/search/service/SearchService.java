package com.beibu.mall.search.service;

import com.beibu.mall.search.dto.SearchRequest;
import com.beibu.mall.search.dto.SearchResponse;
import com.beibu.mall.search.entity.ProductDoc;

/**
 * 搜索服务接口
 *
 * 大白话：定义搜索服务能做什么
 */
public interface SearchService {

    /**
     * 搜索商品
     *
     * @param request 搜索请求参数
     * @return 搜索结果
     */
    SearchResponse search(SearchRequest request);

    /**
     * 同步商品到 ES（新增或更新）
     *
     * @param doc 商品文档
     */
    void syncProduct(ProductDoc doc);

    /**
     * 从 ES 删除商品
     *
     * @param productId 商品 ID
     */
    void deleteProduct(Long productId);
}
