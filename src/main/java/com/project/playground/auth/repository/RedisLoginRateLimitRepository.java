package com.project.playground.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 계정별·IP별 로그인 시도 카운터를 Redis에서 관리한다.
 */
@Repository
@RequiredArgsConstructor
public class RedisLoginRateLimitRepository {

    // 기존 로그인 세션 Key와 분리하여 시도 횟수를 관리한다.
    private static final String KEY_PREFIX = "auth:login:rate-limit:";

    // 횟수 확인, 증가, 최초 만료 설정을 하나의 Lua 실행으로 처리한다.
    private static final RedisScript<Long> ACQUIRE_SCRIPT = RedisScript.of(new ClassPathResource("redis/login-rate-limit/acquire.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 이번 인증 시도를 허용할 수 있는지 확인하고, 허용하면 횟수를 증가시킨다.
     * <p>
     * subject는 호출 계층에서 생성한 계정 또는 IP 식별자다.
     * 반환값 0은 허용, 양수는 재시도까지 남은 밀리초다.
     */
    public long acquire(String subject, int maxAttempts, Duration window) {
        long windowMillis = window.toMillis();

        // 설정 오류가 제한 우회로 이어지지 않도록 실행 전에 검증한다.
        if (maxAttempts < 1 || windowMillis < 1) {
            throw new IllegalArgumentException("로그인 제한 횟수와 시간은 0보다 커야 합니다.");
        }

        Long remainingMillis = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(buildKey(subject)),
                Integer.toString(maxAttempts),
                Long.toString(windowMillis)
        );

        // 결과를 확인할 수 없는 경우 인증을 허용하는 값으로 대체하지 않는다.
        if (remainingMillis == null || remainingMillis < 0) {
            throw new IllegalStateException("로그인 시도 제한 상태를 확인하지 못했습니다.");
        }

        return remainingMillis;
    }

    /**
     * 로그인 성공 시 해당 계정의 카운터를 초기화한다.
     * IP 카운터에는 호출하지 않는다.
     */
    public void reset(String subject) {
        Boolean deleted = redisTemplate.delete(buildKey(subject));

        // false는 이미 만료되어 삭제할 Key가 없는 정상적인 경우다.
        if (deleted == null) {
            throw new IllegalStateException("로그인 시도 카운터를 초기화하지 못했습니다.");
        }
    }

    // 계정과 IP의 구분을 포함한 내부 식별자로 카운터 Key를 생성한다.
    private String buildKey(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("로그인 제한 식별자는 필수입니다.");
        }

        return KEY_PREFIX + subject;
    }
}
