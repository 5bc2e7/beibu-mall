package com.beibu.mall.gateway.filter;

import com.beibu.mall.gateway.config.WhiteListConfig;
import com.beibu.mall.gateway.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局认证过滤器：拦截所有请求，验证 JWT 令牌
 *
 * 什么是过滤器？
 * 过滤器就像"安检站"，所有请求都要经过它。在这里我们可以检查请求是否合法。
 *
 * 什么是 GlobalFilter？
 * GlobalFilter 是 Spring Cloud Gateway 提供的全局过滤器接口，
 * 所有经过网关的请求都会被它拦截。
 *
 * 为什么要做登录校验？
 * 想象一个商场：没有门禁的话，任何人都能进入后台仓库（微服务），
 * 这样就不安全了。所以需要在入口处（网关）检查每个人是否有通行证（JWT）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final WhiteListConfig whiteListConfig;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 核心方法：拦截并处理每个请求
     *
     * @param exchange 请求上下文（包含请求和响应的所有信息）
     * @param chain 过滤器链（调用 chain.filter() 就是放行，不调用就是拦截）
     * @return Mono<Void>（WebFlux 的异步响应，就像 CompletableFuture）
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 安全要求：规范化路径，防止路径遍历攻击
        // 例如：/api/user/login/ 或 /api/user/./login 应该被规范化为 /api/user/login
        String normalizedPath = normalizePath(path);

        // 1. 检查是否是白名单接口（不需要登录就能访问）
        if (isWhiteListed(normalizedPath)) {
            log.debug("白名单接口，放行: {}", normalizedPath);
            return chain.filter(exchange);  // 放行
        }

        // 2. 获取 Authorization 请求头
        // 前端每次请求都会在请求头里带上：Authorization: Bearer xxxxxx
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 3. 检查是否有 Authorization 头
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("请求缺少 Authorization 头, 路径: {}", sanitizeLogMessage(normalizedPath));
            return unauthorizedResponse(exchange, "未登录，请先登录");
        }

        // 4. 提取 JWT 令牌（去掉 "Bearer " 前缀）
        String token = authHeader.substring(7);

        // 5. 验证 JWT 令牌是否有效
        if (!jwtUtil.validateToken(token)) {
            log.warn("JWT 令牌无效或已过期, 路径: {}", sanitizeLogMessage(normalizedPath));
            return unauthorizedResponse(exchange, "登录已过期，请重新登录");
        }

        // 6. 从 JWT 中获取用户信息，传递给下游服务
        // 这样下游服务就不需要再解析 JWT 了，直接从请求头里取用户信息
        Long userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);

        // 7. 安全要求：清理用户信息，防止请求头注入
        String safeUserId = String.valueOf(userId);
        String safeUsername = sanitizeHeaderValue(username);

        // 8. 把用户信息添加到请求头中，传递给下游服务
        // 什么是"mutate"？就是创建一个新的请求，在原请求基础上添加新内容
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", safeUserId)      // 用户ID
                .header("X-User-Name", safeUsername)   // 用户名
                .build();

        log.debug("认证通过，用户ID: {}, 请求路径: {}", safeUserId, sanitizeLogMessage(normalizedPath));

        // 9. 放行，把修改后的请求传递给下游服务
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 规范化路径，防止路径遍历攻击
     *
     * @param path 原始路径
     * @return 规范化后的路径
     */
    private String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        // 移除路径中的 ./ 和多余的 /
        // 例如：/api/user/./login/ -> /api/user/login
        return path.replaceAll("/\\.\\.?/", "/")
                   .replaceAll("/+", "/")
                   .replaceAll("/$", "");
    }

    /**
     * 清理请求头值，防止请求头注入
     *
     * @param value 原始值
     * @return 清理后的值
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        // 移除换行符、回车符等控制字符
        // 这些字符可能被用于 HTTP 头注入攻击
        return value.replaceAll("[\\r\\n\\t]", "")
                   .replaceAll("[^a-zA-Z0-9\\-_\\.\\s]", "");
    }

    /**
     * 清理日志消息，防止日志注入
     *
     * @param message 原始消息
     * @return 清理后的消息
     */
    private String sanitizeLogMessage(String message) {
        if (message == null) {
            return null;
        }
        // 移除换行符、回车符等控制字符
        // 这些字符可能被用于日志注入攻击
        return message.replaceAll("[\\r\\n\\t]", "");
    }

    /**
     * 检查路径是否在白名单中
     *
     * @param path 请求路径
     * @return true=在白名单中（不需要认证），false=不在白名单中（需要认证）
     */
    private boolean isWhiteListed(String path) {
        for (String pattern : whiteListConfig.getUrls()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回 401 未授权响应
     *
     * 为什么要返回 JSON 格式？
     * 因为前端需要解析错误信息，如果返回 HTML 错误页面，前端很难处理。
     * 统一返回 JSON 格式，前端可以统一处理错误。
     *
     * @param exchange 请求上下文
     * @param message 错误信息
     * @return Mono<Void>
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);  // 401 状态码
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 构建错误响应体
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("code", 401);
        errorBody.put("msg", message);
        errorBody.put("data", null);

        try {
            String json = objectMapper.writeValueAsString(errorBody);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("序列化错误响应失败", e);
            return response.setComplete();
        }
    }

    /**
     * 过滤器顺序：数字越小，优先级越高
     * 我们设置为 -100，确保认证过滤器在其他过滤器之前执行
     * 就像安检站要设在商场入口，不能设在商场里面
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
