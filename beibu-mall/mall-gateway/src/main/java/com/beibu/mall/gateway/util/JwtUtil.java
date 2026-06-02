package com.beibu.mall.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 工具类：解析和验证 JWT 令牌
 *
 * 什么是 JWT？
 * JWT（JSON Web Token）是一种"通行证"，用户登录后由用户服务签发，
 * 网关收到请求时验证这张"通行证"是否有效。
 *
 * JWT 的结构：Header.Payload.Signature
 * - Header：声明算法类型（如 HS256）
 * - Payload：存放用户信息（如用户ID、用户名）
 * - Signature：签名，防止令牌被篡改
 *
 * 为什么网关要验证 JWT？
 * 想象一个小区：门卫（网关）检查每个进入者是否有合法通行证（JWT），
 * 如果没有或通行证过期，就不让进。这样就保护了小区里的住户（微服务）。
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * 密钥：用于签名和验证 JWT
     * 注意：这个密钥必须和用户服务签发 JWT 时用的密钥一样，否则验证会失败
     * 安全要求：必须从配置中心或环境变量读取，不能使用硬编码默认值
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 预期的 JWT 签发者
     * 安全要求：验证 issuer 可以防止使用其他服务签发的 JWT
     */
    @Value("${jwt.issuer:beibu-mall}")
    private String issuer;

    /**
     * 获取签名密钥
     * 什么是签名密钥？就像盖章的印章，只有拥有正确印章的人才能验证通行证的真伪
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 解析 JWT 令牌，获取其中的用户信息
     *
     * @param token JWT 令牌字符串
     * @return 解析后的用户信息（Claims）
     * @throws ExpiredJwtException 令牌过期时抛出此异常
     * @throws Exception 其他解析错误（如签名无效、格式错误等）
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())  // 设置验证密钥
                    .requireIssuer(issuer)        // 验证 issuer，防止使用其他服务的 JWT
                    .build()
                    .parseSignedClaims(token)     // 解析并验证签名
                    .getPayload();                // 获取 Payload（用户信息）
        } catch (ExpiredJwtException e) {
            // 令牌过期：就像通行证过期了，需要重新办理
            log.warn("JWT 令牌已过期: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // 其他错误：令牌格式错误、签名无效等
            log.warn("JWT 令牌解析失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 验证 JWT 令牌是否有效
     *
     * @param token JWT 令牌字符串
     * @return true=有效，false=无效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            // 任何异常都说明令牌无效
            return false;
        }
    }

    /**
     * 从 JWT 令牌中获取用户ID
     *
     * @param token JWT 令牌字符串
     * @return 用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        // 从 Payload 中获取 "userId" 字段
        // 用户服务签发 JWT 时会把用户ID放进去
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        } else if (userId instanceof Long) {
            return (Long) userId;
        } else if (userId instanceof String) {
            return Long.parseLong((String) userId);
        }
        return null;
    }

    /**
     * 从 JWT 令牌中获取用户名
     *
     * @param token JWT 令牌字符串
     * @return 用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();  // Subject 通常存放用户名
    }
}
