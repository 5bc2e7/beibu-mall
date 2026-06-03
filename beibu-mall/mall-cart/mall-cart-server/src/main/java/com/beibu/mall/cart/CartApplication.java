package com.beibu.mall.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 购物车服务启动类
 *
 * @SpringBootApplication：Spring Boot 核心注解，包含自动配置、组件扫描等
 *
 * @EnableDiscoveryClient：启用服务注册与发现
 * 购物车服务启动后会自动注册到 Nacos，网关就能发现它
 *
 * @EnableFeignClients：启用 OpenFeign 客户端
 * 告诉 Spring："我要用 Feign 调用其他服务了，请帮我扫描 @FeignClient 注解的接口"
 * basePackages 指定扫描哪个包下的 Feign 接口
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.beibu.mall.product.api.feign")
public class CartApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
