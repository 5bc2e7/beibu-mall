package com.beibu.mall.seckill.config;

import com.beibu.mall.seckill.mq.SeckillMessageProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 秒杀模块测试配置
 *
 * 集中管理 mock bean，避免在各个测试类里重复声明 @MockitoBean。
 * 测试类通过 @Import(SeckillTestConfig.class) 引入即可。
 */
@TestConfiguration
public class SeckillTestConfig {

    @Bean
    public RocketMQTemplate rocketMQTemplate() {
        return Mockito.mock(RocketMQTemplate.class);
    }

    @Bean
    public SeckillMessageProducer seckillMessageProducer() {
        return Mockito.mock(SeckillMessageProducer.class);
    }
}
