package com.beibu.mall.search;

import com.beibu.mall.search.dto.SearchRequest;
import com.beibu.mall.search.dto.SearchResponse;
import com.beibu.mall.search.entity.ProductDoc;
import com.beibu.mall.search.repository.ProductSearchRepository;
import com.beibu.mall.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SearchServiceTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ProductSearchRepository productSearchRepository;

    @BeforeEach
    void setUp() {
        // 清空索引
        productSearchRepository.deleteAll();

        // 准备测试数据
        ProductDoc product1 = new ProductDoc();
        product1.setId(1L);
        product1.setCategoryId(1L);
        product1.setCategoryName("虾类");
        product1.setName("北部湾大对虾");
        product1.setOrigin("广西北海");
        product1.setIsFresh(1);
        product1.setMinPrice(new BigDecimal("89.00"));
        product1.setMaxPrice(new BigDecimal("168.00"));
        product1.setStatus(1);
        product1.setCreateTime(LocalDateTime.of(2024, 1, 15, 10, 30, 0));

        ProductDoc product2 = new ProductDoc();
        product2.setId(2L);
        product2.setCategoryId(1L);
        product2.setCategoryName("虾类");
        product2.setName("基围虾");
        product2.setOrigin("广东湛江");
        product2.setIsFresh(1);
        product2.setMinPrice(new BigDecimal("69.00"));
        product2.setMaxPrice(new BigDecimal("128.00"));
        product2.setStatus(1);
        product2.setCreateTime(LocalDateTime.of(2024, 1, 16, 10, 30, 0));

        ProductDoc product3 = new ProductDoc();
        product3.setId(3L);
        product3.setCategoryId(2L);
        product3.setCategoryName("鱼类");
        product3.setName("金鲳鱼");
        product3.setOrigin("广西北海");
        product3.setIsFresh(0);
        product3.setMinPrice(new BigDecimal("45.00"));
        product3.setMaxPrice(new BigDecimal("88.00"));
        product3.setStatus(1);
        product3.setCreateTime(LocalDateTime.of(2024, 1, 17, 10, 30, 0));

        productSearchRepository.saveAll(List.of(product1, product2, product3));
    }

    @Test
    void testSearchByKeyword() {
        SearchRequest request = new SearchRequest();
        request.setKeyword("对虾");

        SearchResponse response = searchService.search(request);

        assertNotNull(response);
        assertTrue(response.getTotal() >= 1, "Should find at least 1 product with keyword '对虾'");
        assertTrue(response.getProducts().stream()
            .anyMatch(p -> p.getName().contains("对虾")), "Should contain product with '对虾' in name");
    }

    @Test
    void testSearchByCategory() {
        SearchRequest request = new SearchRequest();
        request.setCategoryId(1L);

        SearchResponse response = searchService.search(request);

        assertNotNull(response);
        assertEquals(2, response.getTotal());
    }

    @Test
    void testSearchByPriceRange() {
        SearchRequest request = new SearchRequest();
        request.setMinPrice(new BigDecimal("60.00"));
        request.setMaxPrice(new BigDecimal("100.00"));

        SearchResponse response = searchService.search(request);

        assertNotNull(response);
        assertTrue(response.getTotal() >= 1, "Should find at least 1 product in price range");
    }

    @Test
    void testSearchByFresh() {
        SearchRequest request = new SearchRequest();
        request.setIsFresh(1);

        SearchResponse response = searchService.search(request);

        assertNotNull(response);
        assertEquals(2, response.getTotal());
    }

    @Test
    void testSearchWithSort() {
        SearchRequest request = new SearchRequest();
        request.setCategoryId(1L);
        request.setSortField("minPrice");
        request.setSortOrder("asc");

        SearchResponse response = searchService.search(request);

        assertNotNull(response);
        assertEquals(2, response.getTotal());
        // 价格最低的应该排在前面
        assertTrue(response.getProducts().get(0).getMinPrice().compareTo(
            response.getProducts().get(1).getMinPrice()) <= 0);
    }
}
