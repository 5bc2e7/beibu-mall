package com.beibu.mall.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.beibu.mall")
@EnableFeignClients(basePackages = {"com.beibu.mall.product.api.feign"})
@EnableScheduling
@MapperScan("com.beibu.mall.seckill.mapper")
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
        System.out.println("====================================");
        System.out.println("   秒杀服务启动成功！端口：9008");
        System.out.println("====================================");
    }
}
