package com.beibu.mall.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户服务启动类
 *
 * @SpringBootApplication 是 Spring Boot 的核心注解，包含：
 * - @Configuration：标记为配置类
 * - @EnableAutoConfiguration：开启自动配置
 * - @ComponentScan：扫描当前包及子包下的组件
 *
 * scanBasePackages = "com.beibu.mall"：
 * 默认只扫描 com.beibu.mall.user 包，但 GlobalExceptionHandler 在 com.beibu.mall.common 包
 * 扩大扫描范围确保公共模块的组件也能被 Spring 管理
 *
 * @EnableDiscoveryClient：开启服务注册发现，启动时自动注册到 Nacos
 * @MapperScan：告诉 MyBatis 去哪个包下找 Mapper 接口
 */
@SpringBootApplication(scanBasePackages = "com.beibu.mall")
@EnableDiscoveryClient
@MapperScan("com.beibu.mall.user.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
