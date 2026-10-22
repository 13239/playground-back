package com.project.playground.domain.gomoku.service;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

/** 기존 원자적 고정 시간 창을 별도 계정 키로 재사용해 반복 조회·해시 계산·방 이력 생성을 제한한다. */
@Service
public class RoomRateLimitService {
    private static final RedisScript<Long> ACQUIRE = RedisScript.of(new ClassPathResource("redis/login-rate-limit/acquire.lua"), Long.class);
    private final StringRedisTemplate redis;
    private final String namespace;

    public RoomRateLimitService(StringRedisTemplate redis, @Value("${gomoku.room.rate-limit.namespace:gomoku:room:v1}") String namespace) {
        if (!namespace.matches("[a-zA-Z0-9:_-]{1,100}")) throw new IllegalArgumentException("오목 요청 제한 키 설정이 올바르지 않습니다.");
        this.redis = redis;
        this.namespace = namespace;
    }

    public void search(Long userId) { acquire(userId, "search", 120, 60_000); }
    public void create(Long userId) { acquire(userId, "create", 10, 600_000); }
    public void close(Long userId) { acquire(userId, "close", 30, 60_000); }
    // 대기실 조회는 2초 간격 동기화를 허용하고 비밀번호 검증·준비 변경은 별도 예산으로 제한한다.
    public void state(Long userId) { acquire(userId, "state", 120, 60_000); }
    public void join(Long userId) { acquire(userId, "join", 20, 300_000); }
    public void ready(Long userId) { acquire(userId, "ready", 30, 60_000); }
    public void leave(Long userId) { acquire(userId, "leave", 30, 60_000); }
    // 문서 열기·닫기는 상태 폴링과 별개이며 종료 알림의 중복 전송도 제한한다.
    public void lifecycle(Long userId) { acquire(userId, "lifecycle", 60, 60_000); }
    // 착수·추첨·무르기와 스트림 연결은 조회 예산과 분리한다.
    public void game(Long userId) { acquire(userId, "game", 120, 60_000); }
    public void events(Long userId) { acquire(userId, "events", 30, 60_000); }

    private void acquire(Long userId, String operation, int limit, long windowMillis) {
        try {
            Long remaining = redis.execute(ACQUIRE, List.of(namespace + ":" + operation + ":" + userId), Integer.toString(limit), Long.toString(windowMillis));
            if (remaining == null || remaining < 0) throw new BusinessException(ErrorCode.ROOM_SERVICE_UNAVAILABLE);
            if (remaining > 0) throw new RoomRateLimitException(Math.ceilDiv(remaining, 1000L));
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.ROOM_SERVICE_UNAVAILABLE);
        }
    }
}
