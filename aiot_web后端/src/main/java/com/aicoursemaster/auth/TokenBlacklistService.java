package com.aicoursemaster.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:bl:";

    private final StringRedisTemplate stringRedisTemplate;

    public void blacklistUntilNaturalExpiry(String token, JwtService jwtService) {
        if (token == null || token.isBlank()) {
            return;
        }
        long expEpoch = jwtService.getExpirationEpochSeconds(token);
        long now = Instant.now().getEpochSecond();
        long ttl = expEpoch - now;
        if (ttl <= 0) {
            return;
        }
        String key = PREFIX + sha256Hex(token);
        stringRedisTemplate.opsForValue().set(key, "1", ttl, TimeUnit.SECONDS);
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = PREFIX + sha256Hex(token);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
