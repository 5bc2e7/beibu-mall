package com.beibu.mall.order.feign;

import com.beibu.mall.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class SearchFeignClientFallbackFactory implements FallbackFactory<SearchFeignClient> {

    @Override
    public SearchFeignClient create(Throwable cause) {
        log.error("搜索服务调用失败，执行降级逻辑，原因: {}", cause.getMessage(), cause);

        return new SearchFeignClient() {
            @Override
            public Result<List<SearchResultVO>> searchProducts(String keyword, int page, int size) {
                log.warn("搜索服务不可用，返回空结果，keyword: {}", keyword);
                return Result.ok(Collections.emptyList());
            }
        };
    }
}
