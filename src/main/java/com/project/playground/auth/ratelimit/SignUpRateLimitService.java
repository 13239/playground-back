package com.project.playground.auth.ratelimit;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 공개 가입의 반복 DB 조회·Argon2 계산을 제한한다. 공용 IP를 고려해 기본 120회/분으로 분리한다. */
@Service
public class SignUpRateLimitService {

    // 로그인과 같은 원자적 고정 시간 창 스크립트를 별도의 가입 키로 실행한다.
    private static final RedisScript<Long> ACQUIRE_SCRIPT = RedisScript.of(
            new ClassPathResource("redis/login-rate-limit/acquire.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final long windowMillis;

    public SignUpRateLimitService(StringRedisTemplate redisTemplate,
                                  @Value("${auth.sign-up.rate-limit.ip-max-attempts:120}") int maxAttempts,
                                  @Value("${auth.sign-up.rate-limit.ip-window-seconds:60}") long windowSeconds) {
        if (maxAttempts < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("회원가입 제한 횟수와 시간은 0보다 커야 합니다.");
        }
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.windowMillis = Math.multiplyExact(windowSeconds, 1_000L);
    }

    public void beforeSignUp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("회원가입 접속 주소를 확인하지 못했습니다.");
        }
        try {
            Long remainingMillis = redisTemplate.execute(ACQUIRE_SCRIPT,
                    List.of("auth:sign-up:rate-limit:ip:" + fingerprint(clientIp)),
                    Integer.toString(maxAttempts), Long.toString(windowMillis));
            if (remainingMillis == null || remainingMillis < 0) {
                throw new BusinessException(ErrorCode.SIGN_UP_SERVICE_UNAVAILABLE);
            }
            if (remainingMillis > 0) {
                throw new SignUpRateLimitException(Math.ceilDiv(remainingMillis, 1_000L));
            }
        } catch (DataAccessException e) {
            // 제한 상태를 확인할 수 없으면 가입을 허용하지 않으며 저장소 원문 오류를 노출하지 않는다.
            throw new BusinessException(ErrorCode.SIGN_UP_SERVICE_UNAVAILABLE);
        }
    }

    // Redis에는 IP 원문을 남기지 않고 고정 길이 지문을 사용한다.
    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
