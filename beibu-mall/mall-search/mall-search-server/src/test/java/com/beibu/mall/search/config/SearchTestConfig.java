package com.beibu.mall.search.config;

import com.beibu.mall.search.mq.ProductSyncConsumer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 搜索模块测试配置
 *
 * 集中管理 mock bean，避免在各个测试类里重复声明 @MockitoBean。
 * 测试类通过 @Import(SearchTestConfig.class) 引入即可。
 */
@TestConfiguration
public class SearchTestConfig {

    @MockitoBean
    private ProductSyncConsumer productSyncConsumer;
}
