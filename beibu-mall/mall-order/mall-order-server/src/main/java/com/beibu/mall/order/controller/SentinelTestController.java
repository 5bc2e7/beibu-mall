package com.beibu.mall.order.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.order.feign.SearchFeignClient;
import com.beibu.mall.order.feign.SearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Profile("dev")
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class SentinelTestController {

    private final SearchFeignClient searchFeignClient;

    @GetMapping("/search")
    public Result<List<SearchResultVO>> testSearchFallback(
            @RequestParam(defaultValue = "海鲜") String keyword) {
        log.info("测试搜索服务调用，keyword: {}", keyword);
        return searchFeignClient.searchProducts(keyword, 1, 10);
    }

    @GetMapping("/rate-limit")
    public Result<String> testRateLimit() {
        log.info("测试限流接口被调用");
        return Result.ok("正常响应，时间戳: " + System.currentTimeMillis());
    }

    @GetMapping("/rules")
    public Result<String> showRules() {
        return Result.ok("请访问 Sentinel Dashboard 查看规则: http://localhost:8080");
    }
}
