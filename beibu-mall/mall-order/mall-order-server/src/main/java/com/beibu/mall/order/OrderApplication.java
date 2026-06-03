package com.beibu.mall.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类
 *
 * @SpringBootApplication：Spring Boot 应用的入口
 * 它是一个组合注解，包含：
 * - @Configuration：标记为配置类
 * - @EnableAutoConfiguration：开启自动配置
 * - @ComponentScan：扫描当前包及子包下的组件
 *
 * @EnableFeignClients：开启 Feign 客户端
 * basePackages 指定要扫描的 Feign 客户端包路径
 * 这样 Spring 才能自动创建 Feign 客户端的代理对象
 *
 * @MapperScan：扫描 Mapper 接口
 * 告诉 MyBatis-Plus 去哪里找 Mapper 接口
 */
@SpringBootApplication(scanBasePackages = "com.beibu.mall")
@EnableFeignClients(basePackages = {
    "com.beibu.mall.product.api.feign",
    "com.beibu.mall.inventory.api.feign"
})
@MapperScan("com.beibu.mall.order.mapper")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
        System.out.println("====================================");
        System.out.println("   订单服务启动成功！端口：9005");
        System.out.println("====================================");
    }
}
