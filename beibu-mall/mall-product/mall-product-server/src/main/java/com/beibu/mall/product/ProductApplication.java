package com.beibu.mall.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商品服务启动类
 *
 * @SpringBootApplication 是 Spring Boot 的核心注解，它包含：
 * - @SpringBootConfiguration：标记这是一个配置类
 * - @EnableAutoConfiguration：启用自动配置（Spring Boot 会根据依赖自动配置 Bean）
 * - @ComponentScan：扫描当前包及子包下的所有组件（@Controller、@Service 等）
 *
 * @EnableDiscoveryClient 启用服务注册与发现
 * 这样商品服务启动后会自动注册到 Nacos，网关和其他服务就能发现它
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
