package com.project.playground.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(Long id, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(buildKey(refreshToken), id.toString(), ttl);
    }

    public Optional<Long> findById(String refreshToken) {
        String id = redisTemplate.opsForValue().get(buildKey(refreshToken));

        return Optional.ofNullable(id).map(Long::valueOf);
    }

    // Refresh Token 재사용을 막기 위해 사용자 ID 조회와 토큰 삭제를 한 번에 처리
    public Optional<Long> consume(String refreshToken) {
        String id = redisTemplate.opsForValue().getAndDelete(buildKey(refreshToken));

        return Optional.ofNullable(id).map(Long::valueOf);
    }

    // Redis 데이터 유출 시 원본 토큰을 보호하기 위해 SHA-256으로 해시하고 접두사를 붙여 Key 생성
    private String buildKey(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));

            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
