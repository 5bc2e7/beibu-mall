package com.beibu.mall.user.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 *
 * 不依赖 Spring 容器，通过反射设置 @Value 字段。
 * 测试 JWT 生成、解析、无效Token、过期Token 等场景。
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    private static final String TEST_SECRET = "beibu-mall-test-secret-key-256bits!";  // 32字节 = 256位
    private static final String TEST_ISSUER = "beibu-mall-test";

    @BeforeEach
    void setUp() throws Exception {
        // 通过反射设置 @Value 注入的私有字段
        setField(jwtUtil, "secret", TEST_SECRET);
        setField(jwtUtil, "issuer", TEST_ISSUER);
        setField(jwtUtil, "expiration", 86400000L);  // 24小时
    }

    @Test
    @DisplayName("生成Token成功 - Token不为空")
    void generateToken_success() {
        // when
        String token = jwtUtil.generateToken(1L, "testuser");

        // then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        // JWT 由三段组成，用 . 分隔
        assertEquals(2, token.chars().filter(c -> c == '.').count(),
                "JWT Token 应包含两段分隔符（三段结构）");
    }

    @Test
    @DisplayName("解析有效Token - 正确提取userId和username")
    void parseToken_validToken() {
        // given：生成一个有效Token
        Long userId = 42L;
        String username = "alice";
        String token = jwtUtil.generateToken(userId, username);

        // when
        Claims claims = jwtUtil.parseToken(token);

        // then
        assertNotNull(claims);
        assertEquals(userId, claims.get("userId", Long.class));
        assertEquals(username, claims.getSubject());
        assertEquals(TEST_ISSUER, claims.getIssuer());
    }

    @Test
    @DisplayName("解析无效Token - 抛出JwtException")
    void parseToken_invalidToken() {
        // given
        String invalidToken = "this.is.not-a-valid-jwt-token";

        // when & then
        assertThrows(JwtException.class,
                () -> jwtUtil.parseToken(invalidToken));
    }

    @Test
    @DisplayName("解析过期Token - 抛出ExpiredJwtException")
    void parseToken_expiredToken() throws Exception {
        // given：设置极短过期时间，生成Token后立即过期
        setField(jwtUtil, "expiration", 1L);  // 1毫秒
        String token = jwtUtil.generateToken(1L, "testuser");

        // 等待Token过期
        Thread.sleep(50);

        // when & then
        assertThrows(ExpiredJwtException.class,
                () -> jwtUtil.parseToken(token));
    }

    /**
     * 通过反射设置对象的私有字段值
     * （用于测试中模拟 @Value 注入）
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
