package com.beibu.mall.search.service.impl;

import com.beibu.mall.search.dto.SearchRequest;
import com.beibu.mall.search.dto.SearchResponse;
import com.beibu.mall.search.entity.ProductDoc;
import com.beibu.mall.search.repository.ProductSearchRepository;
import com.beibu.mall.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索服务实现类
 *
 * 大白话：这是搜索服务的核心，负责：
 * 1. 接收搜索请求（关键词、筛选条件、排序方式）
 * 2. 构建 ES 查询语句
 * 3. 执行搜索并返回结果（带高亮）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductSearchRepository productSearchRepository;

    /**
     * 搜索商品
     *
     * 使用 CriteriaQuery 实现简单查询，支持：
     * - 关键词搜索（name、origin、description）
     * - 分类筛选
     * - 活鲜筛选
     * - 价格区间筛选
     * - 排序
     */
    @Override
    public SearchResponse search(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        // ========== 1. 构建查询条件 ==========
        CriteriaQuery query = buildCriteriaQuery(request);

        // ========== 2. 设置分页 ==========
        int page = request.getPage() != null ? request.getPage() - 1 : 0;
        int size = request.getSize() != null ? request.getSize() : 10;
        query.setPageable(PageRequest.of(page, size));

        // ========== 3. 执行查询 ==========
        SearchHits<ProductDoc> searchHits = elasticsearchOperations.search(
            query,
            ProductDoc.class,
            IndexCoordinates.of("product")
        );

        // ========== 4. 转换结果 ==========
        List<SearchResponse.ProductVO> products = searchHits.getSearchHits().stream()
            .map(this::convertToProductVO)
            .collect(Collectors.toList());

        SearchResponse response = new SearchResponse();
        response.setTotal(searchHits.getTotalHits());
        response.setPage(request.getPage() != null ? request.getPage() : 1);
        response.setSize(size);
        response.setProducts(products);
        response.setTook(System.currentTimeMillis() - startTime);

        return response;
    }

    /**
     * 同步商品到 ES
     */
    @Override
    public void syncProduct(ProductDoc doc) {
        productSearchRepository.save(doc);
        log.info("商品已同步到 ES: productId={}", doc.getId());
    }

    /**
     * 从 ES 删除商品
     */
    @Override
    public void deleteProduct(Long productId) {
        productSearchRepository.deleteById(productId);
        log.info("商品已从 ES 删除: productId={}", productId);
    }

    /**
     * 构建查询条件
     */
    private CriteriaQuery buildCriteriaQuery(SearchRequest request) {
        Criteria criteria = new Criteria();

        // 只搜索上架商品
        criteria = criteria.and("status").is(1);

        // 关键词搜索
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            Criteria keywordCriteria = new Criteria("name").matches(request.getKeyword())
                .or(new Criteria("origin").matches(request.getKeyword()))
                .or(new Criteria("description").matches(request.getKeyword()));
            criteria = criteria.and(keywordCriteria);
        }

        // 分类筛选
        if (request.getCategoryId() != null) {
            criteria = criteria.and("categoryId").is(request.getCategoryId());
        }

        // 活鲜筛选
        if (request.getIsFresh() != null) {
            criteria = criteria.and("isFresh").is(request.getIsFresh());
        }

        // 价格区间筛选
        if (request.getMinPrice() != null) {
            criteria = criteria.and("minPrice").greaterThanEqual(request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            criteria = criteria.and("maxPrice").lessThanEqual(request.getMaxPrice());
        }

        CriteriaQuery query = new CriteriaQuery(criteria);

        // 设置排序
        if (request.getSortField() != null && !request.getSortField().isBlank()) {
            Sort sort = "asc".equalsIgnoreCase(request.getSortOrder())
                ? Sort.by(Sort.Direction.ASC, request.getSortField())
                : Sort.by(Sort.Direction.DESC, request.getSortField());
            query.addSort(sort);
        } else {
            // 默认按创建时间倒序
            query.addSort(Sort.by(Sort.Direction.DESC, "createTime"));
        }

        return query;
    }

    /**
     * 将 SearchHit 转换为 ProductVO
     */
    private SearchResponse.ProductVO convertToProductVO(SearchHit<ProductDoc> hit) {
        ProductDoc doc = hit.getContent();
        SearchResponse.ProductVO vo = new SearchResponse.ProductVO();

        vo.setId(doc.getId());
        vo.setCategoryId(doc.getCategoryId());
        vo.setCategoryName(doc.getCategoryName());
        vo.setName(doc.getName());
        vo.setOrigin(doc.getOrigin());
        vo.setIsFresh(doc.getIsFresh());
        vo.setMinPrice(doc.getMinPrice());
        vo.setMaxPrice(doc.getMaxPrice());
        vo.setImage(doc.getImage());

        // 高亮处理
        if (hit.getHighlightFields().containsKey("name") && !hit.getHighlightFields().get("name").isEmpty()) {
            vo.setName(hit.getHighlightFields().get("name").get(0));
        }

        // 转换 SKU 列表
        if (doc.getSkuList() != null) {
            List<SearchResponse.SkuVO> skuVOs = doc.getSkuList().stream()
                .map(sku -> {
                    SearchResponse.SkuVO skuVO = new SearchResponse.SkuVO();
                    skuVO.setId(sku.getId());
                    skuVO.setSpec(sku.getSpec());
                    skuVO.setPrice(sku.getPrice());
                    skuVO.setStock(sku.getStock());
                    skuVO.setImg(sku.getImg());
                    return skuVO;
                })
                .collect(Collectors.toList());
            vo.setSkuList(skuVOs);
        }

        return vo;
    }
}
