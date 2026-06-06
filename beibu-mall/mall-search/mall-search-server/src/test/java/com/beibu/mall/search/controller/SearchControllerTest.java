package com.beibu.mall.search.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.search.dto.SearchRequest;
import com.beibu.mall.search.dto.SearchResponse;
import com.beibu.mall.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController searchController;

    @Test
    void searchProduct_allParams() {
        // Arrange
        SearchResponse mockResponse = new SearchResponse();
        mockResponse.setTotal(5L);
        mockResponse.setPage(2);
        mockResponse.setSize(5);
        mockResponse.setProducts(Collections.emptyList());
        mockResponse.setTook(10L);

        when(searchService.search(any(SearchRequest.class))).thenReturn(mockResponse);

        // Act
        Result<SearchResponse> result = searchController.searchProduct(
                "对虾",
                1L,
                1,
                new BigDecimal("50.00"),
                new BigDecimal("200.00"),
                "price",
                "asc",
                2,
                5
        );

        // Assert
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("success", result.getMsg());
        assertNotNull(result.getData());
        assertEquals(5L, result.getData().getTotal());
        assertEquals(2, result.getData().getPage());
        assertEquals(5, result.getData().getSize());

        // Verify the SearchRequest was built correctly
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(searchService).search(captor.capture());
        SearchRequest capturedRequest = captor.getValue();
        assertEquals("对虾", capturedRequest.getKeyword());
        assertEquals(1L, capturedRequest.getCategoryId());
        assertEquals(1, capturedRequest.getIsFresh());
        assertEquals(new BigDecimal("50.00"), capturedRequest.getMinPrice());
        assertEquals(new BigDecimal("200.00"), capturedRequest.getMaxPrice());
        assertEquals("price", capturedRequest.getSortField());
        assertEquals("asc", capturedRequest.getSortOrder());
        assertEquals(2, capturedRequest.getPage());
        assertEquals(5, capturedRequest.getSize());
    }

    @Test
    void searchProduct_defaultParams() {
        // Arrange
        SearchResponse mockResponse = new SearchResponse();
        mockResponse.setTotal(0L);
        mockResponse.setPage(1);
        mockResponse.setSize(10);
        mockResponse.setProducts(Collections.emptyList());
        mockResponse.setTook(5L);

        when(searchService.search(any(SearchRequest.class))).thenReturn(mockResponse);

        // Act - pass defaults: sortOrder="desc", page=1, size=10
        Result<SearchResponse> result = searchController.searchProduct(
                null, null, null, null, null, null, "desc", 1, 10
        );

        // Assert
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(0L, result.getData().getTotal());

        // Verify default values were passed
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(searchService).search(captor.capture());
        SearchRequest capturedRequest = captor.getValue();
        assertNull(capturedRequest.getKeyword());
        assertNull(capturedRequest.getCategoryId());
        assertNull(capturedRequest.getIsFresh());
        assertNull(capturedRequest.getMinPrice());
        assertNull(capturedRequest.getMaxPrice());
        assertNull(capturedRequest.getSortField());
        assertEquals("desc", capturedRequest.getSortOrder());
        assertEquals(1, capturedRequest.getPage());
        assertEquals(10, capturedRequest.getSize());
    }

    @Test
    void searchProduct_success() {
        // Arrange
        SearchResponse.ProductVO product = new SearchResponse.ProductVO();
        product.setId(1L);
        product.setName("北部湾大对虾");
        product.setCategoryId(1L);
        product.setCategoryName("虾类");
        product.setMinPrice(new BigDecimal("89.00"));
        product.setMaxPrice(new BigDecimal("168.00"));

        SearchResponse mockResponse = new SearchResponse();
        mockResponse.setTotal(1L);
        mockResponse.setPage(1);
        mockResponse.setSize(10);
        mockResponse.setProducts(List.of(product));
        mockResponse.setTook(15L);

        when(searchService.search(any(SearchRequest.class))).thenReturn(mockResponse);

        // Act
        Result<SearchResponse> result = searchController.searchProduct(
                "对虾", null, null, null, null, null, "desc", 1, 10
        );

        // Assert
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().getTotal());
        assertEquals(1, result.getData().getProducts().size());
        assertEquals("北部湾大对虾", result.getData().getProducts().get(0).getName());
        assertEquals(1L, result.getData().getProducts().get(0).getCategoryId());
        assertEquals(new BigDecimal("89.00"), result.getData().getProducts().get(0).getMinPrice());
    }
}
