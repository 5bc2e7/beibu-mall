package com.beibu.mall.cart.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 *
 * 为什么要写这个配置？
 * Spring Boot 自动配置的 RedisTemplate 默认用 JDK 序列化，
 * 存到 Redis 里的数据是一堆乱码（类似 \xac\xed\x00\x05t\x00\x04...），
 * 用 Redis 客户端工具查看时完全看不懂。
 *
 * 这里改成：
 * - key 用 String 序列化（可读的字符串，如 "cart:1001"）
 * - value 用 JSON 序列化（可读的 JSON 格式）
 *
 * 这样在 Redis 里看到的数据就是人能看懂的 JSON。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // JSON 序列化器：把 Java 对象转成 JSON 字符串存到 Redis
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 启用类型信息存储，这样反序列化时能知道要转成什么类型
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        // 支持 Java 8 日期时间类型（LocalDateTime 等）
        objectMapper.registerModule(new JavaTimeModule());

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // String 序列化器：key 用字符串
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // key 序列化方式
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 序列化方式（JSON）
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
