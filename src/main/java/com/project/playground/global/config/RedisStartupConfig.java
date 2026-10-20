package com.project.playground.global.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 모든 환경에서 Redis 연결을 시작 필수 조건으로 검사하며 연결 정보와 시간 제한은 기존 설정을 사용한다. */
@Configuration(proxyBeanMethods = false)
public class RedisStartupConfig {

    /** 웹 서버가 요청을 받기 전 singleton 초기화 단계에서 PING을 확인하고 실패하면 컨텍스트 시작을 중단한다. */
    @Bean
    public SmartInitializingSingleton redisStartupVerifier(StringRedisTemplate redisTemplate) {
        return () -> {
            try {
                String response = redisTemplate.execute(
                        (RedisCallback<String>) connection -> connection.commands().ping());
                if (!"PONG".equals(response)) {
                    throw new IllegalStateException("Redis가 PING에 정상 응답하지 않아 서버 시작을 중단합니다.");
                }
            } catch (DataAccessException exception) {
                // 접속 거부·인증 실패·시간 초과의 원인은 서버 로그에 보존하고 시작 실패로 처리한다.
                throw new IllegalStateException(
                        "Redis 연결 확인에 실패하여 서버 시작을 중단합니다. Redis 실행 상태와 접속 설정을 확인해 주세요.",
                        exception);
            }
        };
    }
}
