package com.crane.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析（HS256），契约见 03 §4.1。密钥生产环境必须以环境变量注入。
 */
@Component
public class JwtUtil {

    @Value("${crane.jwt.secret}")
    private String secret;

    @Value("${crane.jwt.expire-hours:720}")
    private long expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(Long userId, String roleCode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", roleCode)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /** 解析令牌，非法或过期抛 io.jsonwebtoken.JwtException。 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
