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

    /**
     * 개별 로그인 세션 정보를 저장하는 Redis Hash Key
     */
    private static final String KEY_PREFIX = "auth:session:";

    /**
     * 사용자별 로그인 세션 목록을 관리하는 Redis Sorted Set Key
     */
    private static final String USER_SESSIONS_KEY_PREFIX = "auth:user:sessions:";

    /**
     * 비활성화되거나 삭제된 사용자 계정의 인증을 차단하는 Redis Key
     */
    private static final String ACCOUNT_BLOCKED_KEY_PREFIX = "auth:account:blocked:";

    /**
     * 로그인 세션 생성 Lua Script
     * <p>
     * 다음 작업을 Redis에서 원자적으로 처리한다.
     * <p>
     * 1. 사용자 계정 차단 여부 확인
     * 2. 만료된 세션 목록 정리
     * 3. 동일한 sessionId 중복 생성 방지
     * 4. 최대 3개 제한 확인
     * 5. 필요한 경우 가장 오래된 세션을 목록에서 제거
     * 6. 새로운 로그인 세션 Hash 저장 및 TTL 설정
     * 7. 사용자별 Sorted Set에 신규 세션 등록
     * <p>
     * 반환값
     * - 빈 문자열: 세션 생성 성공, 제거된 세션 없음
     * - UUID 문자열: 생성 성공, 기존 세션 하나를 Lua에서 제거
     * - BLOCKED: 차단된 사용자 계정
     * - DUPLICATE: 동일한 세션 ID가 이미 존재
     * - EXPIRED: 신규 세션의 만료 시각이 지남
     */
    private static final RedisScript<String> CREATE_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/create.lua"), String.class);

    /**
     * 사용자 활동 시 세션 만료 시간을 연장
     * 계정 차단 검증과 Hash 및 Sorted Set의 만료 정보 변경을 원자적으로 처리
     */
    private static final RedisScript<Long> TOUCH_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/touch.lua"), Long.class);

    /**
     * 세션 정보 조회, 계정 차단 여부, 사용자별 목록 검증을 Redis에서 원자적으로 처리
     */
    private static final RedisScript<List> FIND_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/find.lua"), List.class);

    /**
     * 계정 차단 여부와 기존 Refresh Token 해시를 원자적으로 검증하고 회전
     */
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/rotate.lua"), Long.class);

    /**
     * 로그인 세션 삭제
     */
    private static final RedisScript<Long> DELETE_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/delete.lua"), Long.class);

    /**
     * 사용자 계정 차단 및 모든 로그인 세션 무효화
     * 계정 차단 Key 생성과 사용자별 로그인 세션 삭제를 Redis에서 원자적으로 처리
     */
    private static final RedisScript<Long> BLOCK_AND_REVOKE_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/block-and-revoke.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 새로운 로그인 세션을 생성한다.
     * <p>
     * 차단된 사용자 계정은 로그인 세션을 생성할 수 없다.
     * 사용자별 동시 로그인은 최대 3개까지 허용한다.
     * 이미 3개가 존재하면 가장 오래된 세션을 종료하고 새로운 세션을 등록한다.
     */
    public boolean create(RedisSession session) {
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("세션 만료 시각이 올바르지 않습니다.");
        }

        /*
         * Lua Script 실행
         *
         * KEYS[1]: 신규 세션 Hash Key
         * KEYS[2]: 사용자별 세션 목록 Sorted Set Key
         * KEYS[3]: 사용자 계정 차단 Key
         *
         * ARGV[1]: sessionId
         * ARGV[2]: 사용자 ID
         * ARGV[3]: username
         * ARGV[4]: Refresh Token 해시
         * ARGV[5]: 로그인 유지 여부
         * ARGV[6]: 생성 시각 (Unix milliseconds)
         * ARGV[7]: 만료 시각 (Unix milliseconds)
         * ARGV[8]: 개별 세션 Hash Key 접두사
         */
        String evictedSessionId = redisTemplate.execute(
                CREATE_SCRIPT,
                List.of(
                        buildKey(session.getSessionId()),
                        buildUserSessionsKey(session.getId()),
                        buildAccountBlockedKey(session.getId())
                ),
                session.getSessionId().toString(),
                session.getId().toString(),
                session.getUsername(),
                session.getRefreshToken(),
                Boolean.toString(session.isKeepLoggedIn()),
                Long.toString(session.getCreatedAt().toEpochMilli()),
                Long.toString(session.getExpiresAt().toEpochMilli()),
                KEY_PREFIX
        );

        /*
         * null: 예상하지 못한 반환값
         * BLOCKED: 계정 차단으로 세션 생성 거부
         * DUPLICATE: 중복 세션 ID
         * EXPIRED: 만료된 세션
         *
         * BLOCKED는 정상적인 인증 거부 결과이므로 false를 반환한다.
         * 나머지 실패 결과는 예상하지 못한 세션 생성 오류로 처리한다.
         */
        if (evictedSessionId == null) {
            throw new IllegalStateException("로그인 세션을 생성하지 못했습니다.");
        }

        if ("BLOCKED".equals(evictedSessionId)) {
            return false;
        }

        if ("DUPLICATE".equals(evictedSessionId) || "EXPIRED".equals(evictedSessionId)) {
            throw new IllegalStateException("로그인 세션을 생성하지 못했습니다.");
        }

        /*
         * 기존 세션이 3개였다면 가장 오래된 세션의 ID가 반환된다.
         *
         * Lua Script에서 사용자별 Sorted Set과 기존 Hash를 함께 삭제한다.
         * Java에서 다시 삭제하지 않아도 되므로 Redis 추가 왕복 호출과 세션 Hash가 뒤늦게 남는 동시성 구간을 없앤다.
         *
         * 빈 문자열이면 제거된 세션이 없다는 의미다.
         */
        return true;
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
         * 0: 연장할 수 없는 세션 (계정 차단 포함)
         * 양수: 갱신된 만료 시각 (Unix milliseconds)
         */
        Long result = redisTemplate.execute(
                TOUCH_SCRIPT,
                List.of(
                        buildKey(sessionId),
                        buildUserSessionsKey(userId),
                        buildAccountBlockedKey(userId)
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

    /**
     * 세션 ID만 알고 있을 때 로그인 세션을 조회한다.
     * <p>
     * Refresh Token에는 사용자 ID가 포함되어 있지 않으므로 Redis Hash에서 사용자 ID를 먼저 조회한다.
     * <p>
     * 조회한 사용자 ID를 사용하여 findBySessionId(UUID, Long)에 위임하고, 실제 세션 유효성 및 계정 차단 여부를 검증한다.
     * <p>
     * Redis 호출은 사용자 ID 조회 1회와 Lua Script 실행 1회, 총 2회 발생한다.
     */
    public Optional<RedisSession> findBySessionId(UUID sessionId) {
        String userId = redisTemplate.<String, String>opsForHash().get(buildKey(sessionId), "id");

        if (userId == null) {
            return Optional.empty();
        }

        return findBySessionId(sessionId, Long.valueOf(userId));
    }

    /**
     * 세션 ID와 사용자 ID를 알고 있을 때 로그인 세션을 조회한다.
     * <p>
     * JWT에는 사용자 ID(sub)와 세션 ID(sid)가 모두 포함되므로 Redis Hash에서 사용자 ID를 미리 조회할 필요가 없다.
     * <p>
     * Lua Script에서 다음 항목을 원자적으로 검증한다.
     * <p>
     * 1. 사용자 계정 차단 여부
     * 2. 세션 필수 정보 존재 여부
     * 3. 요청한 세션 ID와 사용자 ID 일치 여부
     * 4. 세션 만료 여부
     * 5. 사용자별 Sorted Set에 세션이 등록되어 있는지 여부
     * <p>
     * 모든 검증을 통과한 경우 RedisSession을 반환한다.
     * 유효하지 않은 세션은 Optional.empty()를 반환한다.
     * <p>
     * Redis Lua Script 호출은 1회 발생한다.
     */
    public Optional<RedisSession> findBySessionId(UUID sessionId, Long userId) {
        List<?> fields = redisTemplate.execute(
                FIND_SCRIPT,
                List.of(
                        buildKey(sessionId),
                        buildUserSessionsKey(userId),
                        buildAccountBlockedKey(userId)
                ),
                sessionId.toString(),
                userId.toString()
        );

        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }

        if (fields.size() != 7 || fields.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalStateException("Redis 세션 데이터 형식이 올바르지 않습니다.");
        }

        // Lua Script에서 검증한 세션 정보를 RedisSession 객체로 복원한다.
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

    /**
     * 사용자 계정을 차단하고 모든 로그인 세션을 무효화한다.
     * <p>
     * Lua Script에서 다음 작업을 원자적으로 처리한다.
     * <p>
     * 1. 사용자별 Sorted Set에 등록된 세션 목록 조회
     * 2. 계정 차단 Key 생성
     * 3. 해당 사용자의 개별 로그인 세션 Hash 삭제
     * 4. 사용자별 세션 목록 Sorted Set 삭제
     * <p>
     * 차단 Key에는 TTL을 설정하지 않는다.
     * 중복 요청 시에도 차단 상태를 유지하며 남아 있는 세션을 정리한다.
     */
    public void blockAndRevokeAll(Long userId) {
        /*
         * KEYS[1]: 사용자별 세션 목록 Sorted Set Key
         * KEYS[2]: 사용자 계정 차단 Key
         *
         * ARGV[1]: 사용자 ID
         * ARGV[2]: 개별 로그인 세션 Hash Key 접두사
         */
        Long result = redisTemplate.execute(
                BLOCK_AND_REVOKE_SCRIPT,
                List.of(
                        buildUserSessionsKey(userId),
                        buildAccountBlockedKey(userId)
                ),
                userId.toString(),
                KEY_PREFIX
        );

        if (result == null) {
            throw new IllegalStateException("계정 차단 및 로그인 세션 무효화에 실패했습니다.");
        }

    }

    /**
     * 비활성·삭제 차단 Key를 제거하여 회원 재활성화를 허용한다.
     * <p>
     * 호출 전 DB에서 계정의 ACTIVE 전환을 검증하고, 기존 로그인 세션을 모두 무효화해야 한다.
     * 서비스 계층의 행 잠금 및 트랜잭션 내부에서만 호출한다.
     */
    public void unblockAccount(Long userId) {
        redisTemplate.delete(buildAccountBlockedKey(userId));
    }

    // 기존 Refresh Token이 유효할 때만 새로운 토큰으로 교체한다.
    public boolean rotateRefreshToken(UUID sessionId, Long userId, String oldHash, String newHash) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(
                        buildKey(sessionId),
                        buildUserSessionsKey(userId),
                        buildAccountBlockedKey(userId)
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

    /**
     * 인증된 사용자의 해당 로그인 세션을 원자적으로 삭제한다.
     */
    public void deleteBySessionId(UUID sessionId, Long userId) {
        Long result = redisTemplate.execute(
                DELETE_SCRIPT,
                List.of(
                        buildKey(sessionId),
                        buildUserSessionsKey(userId)
                ),
                sessionId.toString(),
                userId.toString()
        );

        if (result == null || (result != 0L && result != 1L)) {
            throw new IllegalStateException("로그인 세션을 안전하게 삭제하지 못했습니다.");
        }
    }

    /**
     * Redis에 사용자 계정 차단 Key가 존재하는지 확인한다.
     * DB 상태와 Redis 차단 상태의 불일치 여부를 확인할 때 사용한다.
     */
    public boolean isAccountBlocked(Long userId) {
        Boolean blocked = redisTemplate.hasKey(buildAccountBlockedKey(userId));

        if (blocked == null) {
            throw new IllegalStateException("계정 차단 상태를 확인하지 못했습니다.");
        }

        return blocked;
    }

    // 개별 로그인 세션의 Redis Key를 생성한다.
    private String buildKey(UUID sessionId) {
        return KEY_PREFIX + sessionId;
    }

    // 사용자별 로그인 세션 목록의 Redis Key를 생성한다.
    private String buildUserSessionsKey(Long id) {
        return USER_SESSIONS_KEY_PREFIX + id;
    }

    // 사용자 계정의 로그인 및 JWT 인증 차단 여부를 저장하는 Redis Key를 생성한다.
    private String buildAccountBlockedKey(Long userId) {
        return ACCOUNT_BLOCKED_KEY_PREFIX + userId;
    }

    private boolean parseBoolean(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalStateException("세션 설정값이 올바르지 않습니다.");
        };
    }
}
