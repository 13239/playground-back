package com.project.playground.domain.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import com.project.playground.global.exception.*;
import java.util.List;

/** 카드 선택은 상태 조회와 별도 예산으로 제한한다. Redis 장애 시 검증을 생략하지 않는다. */
@Component @RequiredArgsConstructor
public class MemoryRateLimit {
    private final StringRedisTemplate redis;
    private static final RedisScript<Long> ACQUIRE = RedisScript.of(new ClassPathResource("redis/login-rate-limit/acquire.lua"), Long.class);
    public void acquire(Long user, String operation) {
        int limit = switch(operation) { case "create", "start", "invite" -> 12; case "join" -> 25; case "flip" -> 600; case "state" -> 1200; default -> 120; };
        Long result;
        try { result = redis.execute(ACQUIRE,List.of("memory:limit:v1:"+operation+":"+user),Integer.toString(limit),"60000"); }
        catch (RuntimeException error) { throw new BusinessException(ErrorCode.MEMORY_UNAVAILABLE); }
        if (result == null || result < 0) throw new BusinessException(ErrorCode.MEMORY_UNAVAILABLE);
        if (result > 0) throw new BusinessException(ErrorCode.MEMORY_RATE_LIMIT);
    }
}
