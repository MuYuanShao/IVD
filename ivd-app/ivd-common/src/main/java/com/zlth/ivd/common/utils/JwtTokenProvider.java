package com.zlth.ivd.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT Token 工具类 — 生成、验证、解析
 * <p>
 * 使用 jjwt 0.12.5，密钥从 application.yml 的 ivd.jwt.secret 读取
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expiration;
    private final long refreshExpiration;

    public JwtTokenProvider(
            @Value("${ivd.jwt.secret}") String secret,
            @Value("${ivd.jwt.expiration}") long expiration,
            @Value("${ivd.jwt.refresh-expiration}") long refreshExpiration) {
        // jjwt 0.12 要求密钥 ≥256 bits (32 bytes)；不足则拒绝启动
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret 长度不足：需要至少 256 bits (32 bytes)，当前仅 " + keyBytes.length + " bytes。" +
                    "请配置 ivd.jwt.secret 为足够长的随机字符串。");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    /** 生成 Access Token */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    /** 生成 Refresh Token（更长有效期，只含 userId） */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiration))
                .signWith(secretKey)
                .compact();
    }

    /** 解析 Token 中的 Claims */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验 Token 是否有效 */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("JWT 无效: {}", e.getMessage());
            return false;
        }
    }

    /** 从 Token 中提取 userId */
    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /** 从 Token 中提取用户名 */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /** 从 Token 中提取角色 */
    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }
}
