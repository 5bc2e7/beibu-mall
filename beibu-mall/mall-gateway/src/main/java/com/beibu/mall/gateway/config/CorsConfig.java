package com.beibu.mall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 跨域配置
 *
 * 什么是跨域？
 * 浏览器有一个"同源策略"：网页只能请求和它自己"同源"的接口。
 * "同源"就是协议、域名、端口都一样。比如：
 * - 网页：http://localhost:3000
 * - 接口：http://localhost:9000
 * 虽然都是 localhost，但端口不同（3000 vs 9000），就算"跨域"。
 *
 * 为什么会有跨域限制？
 * 这是浏览器的安全机制，防止恶意网站偷取其他网站的数据。
 * 比如：你登录了银行网站，然后访问一个恶意网站，
 * 如果没有跨域限制，恶意网站就能用你的登录态去请求银行接口。
 *
 * 为什么在网关统一处理跨域？
 * 1. 方便：不用每个微服务都写一遍跨域配置
 * 2. 统一：所有接口的跨域规则一样，不会出现"有的接口能调，有的不能"
 * 3. 安全：可以在网关统一控制哪些域名能访问
 *
 * 注意：跨域配置要用 CorsWebFilter（WebFlux 版本），
 * 不能用 CorsFilter（WebMVC 版本），因为 Gateway 用的是 WebFlux。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        // 1. 创建跨域配置对象
        CorsConfiguration config = new CorsConfiguration();

        // 2. 允许哪些域名访问
        // 安全要求：只允许特定域名，不能使用 * 通配符
        // 生产环境应该改成具体的前端域名
        config.addAllowedOriginPattern("http://localhost:3000");  // 开发环境
        config.addAllowedOriginPattern("http://localhost:5173");  // Vite 开发服务器
        config.addAllowedOriginPattern("https://www.beibumall.com");  // 生产环境

        // 3. 允许哪些请求方法
        // 安全要求：只允许必要的 HTTP 方法
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");

        // 4. 允许哪些请求头
        // 安全要求：只允许必要的请求头
        config.addAllowedHeader("Authorization");
        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("Accept");
        config.addAllowedHeader("X-Requested-With");

        // 5. 允许携带 Cookie
        // 如果前端需要带 Cookie，要设置为 true
        config.setAllowCredentials(true);

        // 6. 预检请求的缓存时间（秒）
        // 浏览器收到预检响应后，会缓存这个结果，下次同样的请求就不用再发预检请求了
        // 3600 秒 = 1 小时
        config.setMaxAge(3600L);

        // 7. 暴露哪些响应头给前端
        config.addExposedHeader("Authorization");

        // 8. 把配置注册到路径上
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
