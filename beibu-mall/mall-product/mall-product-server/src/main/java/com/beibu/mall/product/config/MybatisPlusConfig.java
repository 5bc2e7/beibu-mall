package com.beibu.mall.product.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 *
 * 配置分页插件，让 MyBatis-Plus 支持分页查询。
 * 为什么需要这个配置？
 * MyBatis-Plus 默认不开启分页，需要手动配置分页插件。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件
     *
     * MybatisPlusInterceptor 是 MyBatis-Plus 的拦截器，可以添加各种插件。
     * PaginationInnerInterceptor 是分页插件，会自动帮我们拼接分页 SQL。
     *
     * 例如：
     *   SELECT * FROM t_spu WHERE status = 1 LIMIT 10 OFFSET 0
     * MyBatis-Plus 会自动添加 LIMIT 和 OFFSET，我们只需要传 page 和 size。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页插件，DbType.MYSQL 表示数据库类型是 MySQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
