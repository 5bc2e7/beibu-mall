package com.beibu.mall.seckill.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 *
 * @Configuration：标记这是一个配置类，Spring 启动时会扫描并执行里面的 @Bean 方法
 * @Bean：告诉 Spring "这个方法返回的对象请放进 IoC 容器，我后面要注入使用"
 *
 * IoC 容器 = Spring 的对象工厂，所有 Bean 都由它创建和管理
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate（操作 Redis 的工具类）
     *
     * 为什么要配置序列化器？
     * Redis 存储的是字节数据，Java 对象需要序列化（转成字节）才能存进去
     * 默认的 JDK 序列化会存一堆乱码，我们用 JSON 序列化，可读性好
     *
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 序列化：用字符串
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 序列化：用 JSON
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 加载秒杀 Lua 脚本
     *
     * DefaultRedisScript：Spring 封装的 Redis 脚本对象
     * ClassPathResource：从 classpath（即 src/main/resources）加载文件
     *
     * 这个 Bean 会被注入到 SeckillService 中使用
     */
    @Bean
    public DefaultRedisScript<Long> seckillLuaScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
