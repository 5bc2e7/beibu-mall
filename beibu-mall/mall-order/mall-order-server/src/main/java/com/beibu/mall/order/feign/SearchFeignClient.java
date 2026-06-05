package com.beibu.mall.order.feign;

import com.beibu.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "search-service", path = "/api/search",
    fallbackFactory = SearchFeignClientFallbackFactory.class)
public interface SearchFeignClient {

    @GetMapping("/product")
    Result<List<SearchResultVO>> searchProducts(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size);
}
