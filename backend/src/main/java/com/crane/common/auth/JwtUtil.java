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

    /** 未绑定手机号时签发的短时效绑定凭据，默认 10 分钟 */
    @Value("${crane.wechat.bind-ticket-minutes:10}")
    private long bindTicketMinutes;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(Long userId, String roleCode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", "access")
                .claim("role", roleCode)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /**
     * 签发绑定凭据（Bind Ticket）：仅用于绑定手机号，携带微信 openid / unionid，
     * 短时效且不能作为业务访问令牌使用（typ=bind）。
     */
    public String createBindTicket(String openId, String unionId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + bindTicketMinutes * 60_000L);
        var builder = Jwts.builder()
                .subject("bind")
                .claim("typ", "bind")
                .claim("openId", openId)
                .issuedAt(now)
                .expiration(expiry);
        if (unionId != null && !unionId.isBlank()) {
            builder.claim("unionId", unionId);
        }
        return builder.signWith(key()).compact();
    }

    /** 是否为绑定凭据（不可当作业务令牌使用） */
    public boolean isBindTicket(Claims claims) {
        return "bind".equals(claims.get("typ", String.class));
    }

    /** 解析令牌，非法或过期抛 io.jsonwebtoken.JwtException。 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
