package com.beibu.mall.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 搜索服务启动类
 *
 * 大白话：这是整个搜索服务的入口，程序从这里开始运行
 *
 * @SpringBootApplication：告诉 Spring "这是一个 Spring Boot 应用"
 * @EnableDiscoveryClient：告诉 Nacos "我要注册到你这里，让其他服务能找到我"
 */
@SpringBootApplication
@EnableDiscoveryClient
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
