package com.project.playground.domain.tetris.service;

import com.project.playground.global.exception.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;

/** 리플레이 검증 비용과 반복 시작을 계정 단위로 제한한다. Redis 장애 시 제한을 우회하지 않는다. */
@Service @RequiredArgsConstructor
public class TetrisRateLimit {
    private static final RedisScript<Long> ACQUIRE = RedisScript.of(new ClassPathResource("redis/login-rate-limit/acquire.lua"), Long.class);
    private final StringRedisTemplate redis;
    public void acquire(Long userId, String operation) {
        int limit = switch (operation) { case "start" -> 30; case "finish" -> 12; default -> 60; };
        long window = operation.equals("start") ? 600_000 : 60_000;
        try {
            Long wait = redis.execute(ACQUIRE, List.of("tetris:v1:" + operation + ":" + userId), "" + limit, "" + window);
            if (wait == null || wait < 0) throw new BusinessException(ErrorCode.TETRIS_UNAVAILABLE);
            if (wait > 0) throw new BusinessException(ErrorCode.TETRIS_RATE_LIMIT_EXCEEDED);
        } catch (DataAccessException exception) { throw new BusinessException(ErrorCode.TETRIS_UNAVAILABLE); }
    }
}
