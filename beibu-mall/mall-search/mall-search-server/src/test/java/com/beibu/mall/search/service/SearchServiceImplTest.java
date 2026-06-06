package com.beibu.mall.search.service;

import com.beibu.mall.search.dto.SearchRequest;
import com.beibu.mall.search.dto.SearchResponse;
import com.beibu.mall.search.entity.ProductDoc;
import com.beibu.mall.search.repository.ProductSearchRepository;
import com.beibu.mall.search.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private ProductSearchRepository productSearchRepository;

    @InjectMocks
    private SearchServiceImpl searchService;

    @Test
    void search_withKeyword() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setKeyword("对虾");
        request.setPage(1);
        request.setSize(10);

        ProductDoc doc = createProductDoc(1L, "北部湾大对虾", 1L, "虾类");
        SearchHits<ProductDoc> searchHits = createSearchHits(List.of(doc), 1L);

        when(elasticsearchOperations.search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        )).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getTotal());
        assertEquals(1, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(1, response.getProducts().size());
        assertEquals("北部湾大对虾", response.getProducts().get(0).getName());
        assertNotNull(response.getTook());

        verify(elasticsearchOperations).search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        );
    }

    @Test
    void search_withCategoryId() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setCategoryId(2L);
        request.setPage(1);
        request.setSize(10);

        ProductDoc doc = createProductDoc(3L, "金鲳鱼", 2L, "鱼类");
        SearchHits<ProductDoc> searchHits = createSearchHits(List.of(doc), 1L);

        when(elasticsearchOperations.search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        )).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getTotal());
        assertEquals("金鲳鱼", response.getProducts().get(0).getName());
        assertEquals(2L, response.getProducts().get(0).getCategoryId());
    }

    @Test
    void search_emptyResult() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setKeyword("不存在的商品");
        request.setPage(1);
        request.setSize(10);

        SearchHits<ProductDoc> searchHits = createSearchHits(Collections.emptyList(), 0L);

        when(elasticsearchOperations.search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        )).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(request);

        // Assert
        assertNotNull(response);
        assertEquals(0L, response.getTotal());
        assertTrue(response.getProducts().isEmpty());
    }

    @Test
    void search_nullKeyword() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setKeyword(null);
        request.setPage(1);
        request.setSize(10);

        ProductDoc doc = createProductDoc(1L, "北部湾大对虾", 1L, "虾类");
        SearchHits<ProductDoc> searchHits = createSearchHits(List.of(doc), 1L);

        when(elasticsearchOperations.search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        )).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getTotal());
        verify(elasticsearchOperations).search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        );
    }

    @Test
    void syncProduct_success() {
        // Arrange
        ProductDoc doc = createProductDoc(1L, "北部湾大对虾", 1L, "虾类");
        when(productSearchRepository.save(any(ProductDoc.class))).thenReturn(doc);

        // Act
        searchService.syncProduct(doc);

        // Assert
        verify(productSearchRepository).save(doc);
    }

    @Test
    void deleteProduct_success() {
        // Arrange
        Long productId = 1L;
        doNothing().when(productSearchRepository).deleteById(productId);

        // Act
        searchService.deleteProduct(productId);

        // Assert
        verify(productSearchRepository).deleteById(productId);
    }

    @Test
    void search_withPagination() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setPage(2);
        request.setSize(5);

        ProductDoc doc = createProductDoc(6L, "商品6", 1L, "虾类");
        SearchHits<ProductDoc> searchHits = createSearchHits(List.of(doc), 10L);

        when(elasticsearchOperations.search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        )).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(request);

        // Assert
        assertNotNull(response);
        assertEquals(10L, response.getTotal());
        assertEquals(2, response.getPage());
        assertEquals(5, response.getSize());
    }

    @Test
    void search_withSortField() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setSortField("price");
        request.setSortOrder("asc");
        request.setPage(1);
        request.setSize(10);

        ProductDoc doc1 = createProductDoc(1L, "便宜虾", 1L, "虾类");
        doc1.setMinPrice(new BigDecimal("50.00"));
        ProductDoc doc2 = createProductDoc(2L, "贵虾", 1L, "虾类");
        doc2.setMinPrice(new BigDecimal("100.00"));

        SearchHits<ProductDoc> searchHits = createSearchHits(List.of(doc1, doc2), 2L);

        when(elasticsearchOperations.search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        )).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(request);

        // Assert
        assertNotNull(response);
        assertEquals(2L, response.getTotal());
        assertEquals(2, response.getProducts().size());
    }

    @Test
    void search_withSkuList() {
        // Arrange
        SearchRequest request = new SearchRequest();
        request.setKeyword("对虾");
        request.setPage(1);
        request.setSize(10);

        ProductDoc doc = createProductDoc(1L, "北部湾大对虾", 1L, "虾类");
        ProductDoc.SkuInfo sku = new ProductDoc.SkuInfo();
        sku.setId(101L);
        sku.setSpec("鲜活/500g");
        sku.setPrice(new BigDecimal("89.00"));
        sku.setStock(100);
        sku.setImg("sku1.jpg");
        doc.setSkuList(List.of(sku));

        SearchHits<ProductDoc> searchHits = createSearchHits(List.of(doc), 1L);

        when(elasticsearchOperations.search(
                any(Query.class),
                eq(ProductDoc.class),
                eq(IndexCoordinates.of("product"))
        )).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getProducts().size());
        SearchResponse.ProductVO product = response.getProducts().get(0);
        assertNotNull(product.getSkuList());
        assertEquals(1, product.getSkuList().size());
        assertEquals(101L, product.getSkuList().get(0).getId());
        assertEquals("鲜活/500g", product.getSkuList().get(0).getSpec());
        assertEquals(new BigDecimal("89.00"), product.getSkuList().get(0).getPrice());
    }

    // ========== Helper methods ==========

    private ProductDoc createProductDoc(Long id, String name, Long categoryId, String categoryName) {
        ProductDoc doc = new ProductDoc();
        doc.setId(id);
        doc.setName(name);
        doc.setCategoryId(categoryId);
        doc.setCategoryName(categoryName);
        doc.setOrigin("广西北海");
        doc.setIsFresh(1);
        doc.setMinPrice(new BigDecimal("89.00"));
        doc.setMaxPrice(new BigDecimal("168.00"));
        doc.setImage("product1.jpg");
        doc.setStatus(1);
        doc.setCreateTime(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        return doc;
    }

    @SuppressWarnings("unchecked")
    private SearchHits<ProductDoc> createSearchHits(List<ProductDoc> docs, long totalHits) {
        List<SearchHit<ProductDoc>> hitList = docs.stream()
                .map(doc -> {
                    SearchHit<ProductDoc> hit = mock(SearchHit.class);
                    when(hit.getContent()).thenReturn(doc);
                    when(hit.getHighlightFields()).thenReturn(new HashMap<>());
                    return hit;
                })
                .toList();

        return new SearchHitsImpl<>(
                totalHits,
                TotalHitsRelation.EQUAL_TO,
                1.0f,
                null,
                null,
                null,
                hitList,
                null,
                null,
                null
        );
    }
}
