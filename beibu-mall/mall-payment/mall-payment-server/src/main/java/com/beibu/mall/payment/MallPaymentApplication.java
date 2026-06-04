package com.beibu.mall.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 支付服务启动类
 *
 * 大白话：这是整个支付服务的入口，程序从这里开始运行
 *
 * @SpringBootApplication：Spring Boot 应用的入口
 * 它是一个组合注解，包含：
 * - @Configuration：标记为配置类
 * - @EnableAutoConfiguration：开启自动配置
 * - @ComponentScan：扫描当前包及子包下的组件
 *
 * @MapperScan：扫描 Mapper 接口
 * 告诉 MyBatis-Plus 去哪里找 Mapper 接口
 */
@SpringBootApplication(scanBasePackages = "com.beibu.mall")
@MapperScan("com.beibu.mall.payment.mapper")
public class MallPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallPaymentApplication.class, args);
        System.out.println("====================================");
        System.out.println("   支付服务启动成功！端口：9006");
        System.out.println("====================================");
    }
}
