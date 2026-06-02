package com.beibu.mall.user.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 *
 * 负责生成和解析 JWT Token。
 *
 * JWT 结构（用 . 分隔的三段）：
 * eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature
 * |---- Header ----|  |---- Payload ----|  |-- Signature --|
 *
 * - Header: {"alg": "HS256"}  声明签名算法
 * - Payload: {"sub": "1234567890", "exp": 1234567890}  存放数据
 * - Signature: 对前两段用密钥签名，防止被篡改
 */
@Component
public class JwtUtil {

    /**
     * JWT 密钥
     * 安全要求：必须从配置中心或环境变量读取，不能使用硬编码默认值
     * 注意：网关和用户服务必须使用相同的密钥，否则令牌验证会失败
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * JWT 签发者
     * 安全要求：验证 issuer 可以防止使用其他服务签发的 JWT
     * 注意：网关和用户服务必须使用相同的 issuer
     */
    @Value("${jwt.issuer:beibu-mall}")
    private String issuer;

    /**
     * Token 有效期（毫秒），默认 24 小时
     */
    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    /**
     * 生成 JWT Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)                    // subject（主题）：存用户名
                .claim("userId", userId)              // 自定义字段：存用户ID
                .issuer(issuer)                       // 签发者：用于验证令牌来源
                .issuedAt(now)                        // 签发时间
                .expiration(expiryDate)               // 过期时间
                .signWith(getSecretKey())             // 用密钥签名
                .compact();                           // 生成字符串
    }

    /**
     * 从 Token 中解析出用户信息
     *
     * @param token JWT Token
     * @return 解析后的 Claims（包含 userId、username 等）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())           // 用密钥验证签名
                .build()
                .parseSignedClaims(token)             // 解析 Token
                .getPayload();                        // 获取 Payload
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取签名密钥
     * Keys.hmacShaKeyFor() 要求至少 256 位（32 字节）的密钥
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
