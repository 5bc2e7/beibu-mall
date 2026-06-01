package com.beibu.mall.user.config;

import com.beibu.mall.user.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * 在这里注册拦截器、配置跨域等。
 * 实现 WebMvcConfigurer 接口是 Spring 推荐的配置方式（代替继承 WebMvcConfigurationSupport）。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")      // 拦截 /api/ 开头的所有请求
                .excludePathPatterns(            // 排除不需要登录的接口
                        "/api/user/register",    // 注册
                        "/api/user/login",       // 登录
                        "/doc.html",             // Knife4j 文档
                        "/webjars/**",           // Knife4j 静态资源
                        "/v3/api-docs/**"        // OpenAPI 文档
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
