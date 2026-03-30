package com.aicoursemaster.auth;

import com.aicoursemaster.config.AppAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppAuthProperties authProperties;

    private SecretKey key() {
        byte[] bytes = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.auth.jwt-secret 长度至少 32 字节（256 bit）");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String createToken(long userId, String username) {
        long expSec = authProperties.getJwtExpirationSeconds();
        Date now = new Date();
        Date exp = new Date(now.getTime() + expSec * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationEpochSeconds(String token) {
        try {
            Claims c = parseClaims(token);
            Date exp = c.getExpiration();
            return exp == null ? 0L : exp.getTime() / 1000L;
        } catch (ExpiredJwtException e) {
            Date exp = e.getClaims().getExpiration();
            return exp == null ? 0L : exp.getTime() / 1000L;
        } catch (JwtException e) {
            return 0L;
        }
    }

    public boolean isExpired(String token) {
        try {
            parseClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }
}
