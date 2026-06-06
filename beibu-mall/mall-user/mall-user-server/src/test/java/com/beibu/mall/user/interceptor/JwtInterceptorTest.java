package com.beibu.mall.user.interceptor;

import com.beibu.mall.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JwtInterceptor 单元测试
 *
 * 模拟 HttpServletRequest / HttpServletResponse，
 * 测试 Token 校验拦截器的各种场景。
 */
@ExtendWith(MockitoExtension.class)
class JwtInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private JwtInterceptor jwtInterceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @Test
    @DisplayName("有效Token - 放行请求并设置用户属性")
    void preHandle_validToken_returnsTrue() throws Exception {
        // given
        String token = "valid-jwt-token";
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getUserId(token)).thenReturn(42L);
        when(jwtUtil.getUsername(token)).thenReturn("alice");

        // when
        boolean result = jwtInterceptor.preHandle(request, response, handler);

        // then
        assertTrue(result);
        verify(request).setAttribute("userId", 42L);
        verify(request).setAttribute("username", "alice");
    }

    @Test
    @DisplayName("无Token - 拦截请求返回401")
    void preHandle_noToken_returnsFalse() throws Exception {
        // given：没有 Authorization header
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        // when
        boolean result = jwtInterceptor.preHandle(request, response, handler);

        // then
        assertFalse(result);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertTrue(stringWriter.toString().contains("未登录或Token缺失"));
    }

    @Test
    @DisplayName("无效Token - 拦截请求返回401")
    void preHandle_invalidToken_returnsFalse() throws Exception {
        // given
        String token = "invalid-token";
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(false);
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        // when
        boolean result = jwtInterceptor.preHandle(request, response, handler);

        // then
        assertFalse(result);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertTrue(stringWriter.toString().contains("Token无效或已过期"));
    }
}
