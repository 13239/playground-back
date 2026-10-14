package com.project.playground.auth.repository;

import com.project.playground.global.security.session.RedisSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisSessionRepository {

    private static final String KEY_PREFIX = "auth:session:";

    private static final String USER_SESSIONS_KEY_PREFIX = "auth:user:sessions:";

    /**
     * 로그인 세션 생성 Lua Script
     * <p>
     * 다음 작업을 Redis에서 원자적으로 처리한다.
     * <p>
     * 1. 만료된 세션 목록 정리
     * 2. 동일한 sessionId 중복 생성 방지
     * 3. 최대 3개 제한 확인
     * 4. 필요한 경우 가장 오래된 세션을 목록에서 제거
     * 5. 새로운 로그인 세션 Hash 저장 및 TTL 설정
     * 6. 사용자별 Sorted Set에 신규 세션 등록
     * <p>
     * 반환값
     * - 빈 문자열: 세션 생성 성공, 제거된 세션 없음
     * - UUID 문자열: 생성 성공, 기존 세션 하나 제거
     * - DUPLICATE: 동일한 세션 ID가 이미 존재
     * - EXPIRED: 신규 세션의 만료 시각이 지남
     */
    private static final RedisScript<String> CREATE_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/create.lua"), String.class);

    /**
     * 사용자 활동 시 세션 만료 시간을 연장하는 Lua Script
     * Hash와 Sorted Set의 만료 정보를 원자적으로 변경한다.
     */
    private static final RedisScript<Long> TOUCH_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/touch.lua"), Long.class);

    /**
     * 세션 정보 조회와 사용자별 목록 검증을 Redis에서 원자적으로 처리하는 Lua Script
     */
    private static final RedisScript<List> FIND_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/find.lua"), List.class);

    // Refresh Token 회전
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/rotate.lua"), Long.class);

    // 로그인 세션 삭제
    private static final RedisScript<Long> DELETE_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/delete.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 새로운 로그인 세션을 생성한다.
     * <p>
     * 사용자별 동시 로그인은 최대 3개까지 허용한다.
     * 이미 3개가 존재하면 가장 오래된 세션을 종료하고 새로운 세션을 등록한다.
     */
    public void create(RedisSession session) {
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("세션 만료 시각이 올바르지 않습니다.");
        }

        /*
         * Lua Script 실행
         *
         * KEYS[1]: 신규 세션 Hash Key
         * KEYS[2]: 사용자별 세션 목록 Sorted Set Key
         *
         * ARGV[1]: sessionId
         * ARGV[2]: 사용자 ID
         * ARGV[3]: username
         * ARGV[4]: Refresh Token 해시
         * ARGV[5]: 로그인 유지 여부
         * ARGV[6]: 생성 시각 (Unix milliseconds)
         * ARGV[7]: 만료 시각 (Unix milliseconds)
         */
        String evictedSessionId = redisTemplate.execute(
                CREATE_SCRIPT,
                List.of(
                        buildKey(session.getSessionId()),
                        buildUserSessionsKey(session.getId())
                ),
                session.getSessionId().toString(),
                session.getId().toString(),
                session.getUsername(),
                session.getRefreshToken(),
                Boolean.toString(session.isKeepLoggedIn()),
                Long.toString(session.getCreatedAt().toEpochMilli()),
                Long.toString(session.getExpiresAt().toEpochMilli())
        );

        /*
         * null: 예상하지 못한 반환값
         * DUPLICATE: 중복 세션 ID
         * EXPIRED: 만료된 세션
         *
         * 위 결과는 신규 세션을 정상적으로 생성하지 못한 경우이므로 예외를 발생시킨다.
         */
        if (evictedSessionId == null || "DUPLICATE".equals(evictedSessionId) || "EXPIRED".equals(evictedSessionId)) {
            throw new IllegalStateException("로그인 세션을 생성하지 못했습니다.");
        }

        /*
         * 기존 세션이 3개였다면 가장 오래된 세션의 ID가 반환된다.
         *
         * Lua Script에서 사용자별 Sorted Set에서는 이미 해당 세션을 제거했으므로 여기서는 남아 있는 Redis Hash를 정리한다.
         *
         * 빈 문자열이면 제거된 세션이 없다는 의미다.
         */
        if (!evictedSessionId.isEmpty()) {
            redisTemplate.delete(buildKey(UUID.fromString(evictedSessionId)));
        }
    }

    // 사용자 활동 시 Redis 세션의 만료 시간을 연장한다.
    public Optional<Instant> extendOnActivity(UUID sessionId, Long userId, Duration rememberTtl, Duration idleTtl) {
        long rememberTtlMillis = rememberTtl.toMillis();
        long idleTtlMillis = idleTtl.toMillis();

        if (rememberTtlMillis <= 0 || idleTtlMillis <= 0) {
            throw new IllegalArgumentException("세션 연장 시간은 0보다 커야 합니다.");
        }

        /*
         * Lua Script에서 세션을 검증한 후 로그인 유지 여부에 맞는 TTL을 선택하여 만료 시간을 연장한다.
         *
         * 0: 연장할 수 없는 세션
         * 양수: 갱신된 만료 시각 (Unix milliseconds)
         */
        Long result = redisTemplate.execute(
                TOUCH_SCRIPT,
                List.of(
                        buildKey(sessionId),
                        buildUserSessionsKey(userId)
                ),
                sessionId.toString(),
                userId.toString(),
                Long.toString(rememberTtlMillis),
                Long.toString(idleTtlMillis)
        );

        if (result == null) {
            throw new IllegalStateException("세션 만료 시간 연장에 실패했습니다.");
        }

        if (result == 0L) {
            return Optional.empty();
        }

        return Optional.of(Instant.ofEpochMilli(result));
    }

    public Optional<RedisSession> findBySessionId(UUID sessionId) {
        String sessionKey = buildKey(sessionId);

        /*
         * 사용자별 Sorted Set Key를 만들기 위해 사용자 ID를 먼저 조회한다.
         * 실제 세션 유효성 검증은 아래 Lua Script에서 다시 수행한다.
         */
        String userId = redisTemplate.<String, String>opsForHash().get(sessionKey, "id");

        if (userId == null) {
            return Optional.empty();
        }

        // Lua Script에서 세션 상세 정보와 사용자별 세션 목록을 한 번에 확인한다.
        List<?> fields = redisTemplate.execute(
                FIND_SCRIPT,
                List.of(
                        sessionKey,
                        buildUserSessionsKey(Long.valueOf(userId))
                ),
                sessionId.toString(),
                userId
        );

        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }

        if (fields.size() != 7 || fields.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalStateException("Redis 세션 데이터 형식이 올바르지 않습니다.");
        }

        // Lua Script에서 검증한 세션 정보를 객체로 복원한다.
        RedisSession session = RedisSession.builder()
                .sessionId(UUID.fromString((String) fields.get(0)))
                .id(Long.valueOf((String) fields.get(1)))
                .username((String) fields.get(2))
                .refreshToken((String) fields.get(3))
                .keepLoggedIn(parseBoolean((String) fields.get(4)))
                .createdAt(Instant.ofEpochMilli(Long.parseLong((String) fields.get(5))))
                .expiresAt(Instant.ofEpochMilli(Long.parseLong((String) fields.get(6))))
                .build();

        return Optional.of(session);
    }

    // 기존 Refresh Token이 유효할 때만 새로운 토큰으로 교체한다.
    public boolean rotateRefreshToken(UUID sessionId, Long userId, String oldHash, String newHash) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(
                        buildKey(sessionId),
                        buildUserSessionsKey(userId)
                ),
                sessionId.toString(),
                userId.toString(),
                oldHash,
                newHash
        );

        if (result == null) {
            throw new IllegalStateException("Refresh Token 회전에 실패했습니다.");
        }

        return result == 1L;
    }

    // 유효한 Refresh Token으로 요청했을 때만 해당 세션을 삭제한다.
    public boolean deleteByRefreshToken(UUID sessionId, Long userId, String tokenHash) {
        Long result = redisTemplate.execute(
                DELETE_SCRIPT,
                List.of(
                        buildKey(sessionId),
                        buildUserSessionsKey(userId)
                ),
                sessionId.toString(),
                userId.toString(),
                tokenHash
        );

        if (result == null) {
            throw new IllegalStateException("로그인 세션 삭제에 실패했습니다.");
        }

        return result == 1L;
    }

    // 개별 로그인 세션의 Redis Key를 생성한다.
    private String buildKey(UUID sessionId) {
        return KEY_PREFIX + sessionId;
    }

    // 사용자별 로그인 세션 목록의 Redis Key를 생성한다.
    private String buildUserSessionsKey(Long id) {
        return USER_SESSIONS_KEY_PREFIX + id;
    }

    /**
     * 사용자별 Sorted Set에 저장할 Member를 생성한다.
     * <p>
     * 형식: {sessionId}|{expiresAt}
     * <p>
     * 만료 시각을 함께 저장하는 이유는 Lua Script에서 각 세션의 만료 여부를 판단하고 만료된 세션을 목록에서 정리하기 위해서다.
     */
    private String buildIndexMember(UUID sessionId, Instant expiresAt) {
        return sessionId + "|" + expiresAt.toEpochMilli();
    }

    private boolean parseBoolean(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalStateException("세션 설정값이 올바르지 않습니다.");
        };
    }
}
