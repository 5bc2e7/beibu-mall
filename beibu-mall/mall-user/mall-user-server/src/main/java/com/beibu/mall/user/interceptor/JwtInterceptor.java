package com.beibu.mall.user.interceptor;

import com.beibu.mall.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 *
 * 工作流程：
 * 1. 用户登录后，前端拿到 JWT Token
 * 2. 前端每次请求都带上 Header：Authorization: Bearer <token>
 * 3. 这个拦截器在请求到达 Controller 之前执行：
 *    - 从 Header 取出 Token
 *    - 验证 Token 是否有效（签名正确、未过期）
 *    - 从 Token 中提取 userId，存入 request 属性
 * 4. Controller 通过 request.getAttribute("userId") 获取当前登录用户ID
 *
 * HandlerInterceptor 三个方法的执行顺序：
 * - preHandle()    → Controller 之前（这里做鉴权）
 * - postHandle()   → Controller 之后、视图渲染之前（很少用）
 * - afterCompletion() → 请求完全结束后（清理资源）
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求（跨域时浏览器会先发 OPTIONS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从 Header 中获取 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录或Token缺失\",\"data\":null}");
            return false; // 拦截请求
        }

        String token = authHeader.substring(7); // 去掉 "Bearer " 前缀

        // 验证 Token
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"Token无效或已过期\",\"data\":null}");
            return false;
        }

        // 从 Token 中提取用户信息，存入 request 属性
        // Controller 中可以通过 @RequestAttribute 或 request.getAttribute 获取
        Long userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);

        return true; // 放行请求
    }
}
