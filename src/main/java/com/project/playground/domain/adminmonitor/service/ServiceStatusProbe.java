package com.project.playground.domain.adminmonitor.service;

import com.project.playground.domain.accesspolicy.redis.RedisAccessPolicyRepository;
import com.project.playground.domain.adminchat.AdminChatClient;
import com.project.playground.domain.adminmonitor.dto.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.time.*;
import java.util.Set;

/** 계정·방·메시지를 생성하지 않는 읽기 전용 운영 연결 확인이다. 요청자는 캐시만 읽는다. */
@Component
public class ServiceStatusProbe {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final AdminChatClient chat;
    public ServiceStatusProbe(DataSource source, StringRedisTemplate redis, AdminChatClient chat) {
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2); this.redis = redis; this.chat = chat;
    }
    public ServiceStatus login() {
        return local("LOGIN", () -> {
            jdbc.queryForList("SELECT u.id FROM tbl_user u LEFT JOIN tbl_role r ON r.code=u.role_code LIMIT 1");
            ping();
            Object state = redis.opsForHash().get(RedisAccessPolicyRepository.KEY, "state");
            return "ready".equals(state) ? "NORMAL" : "pending".equals(state) ? "DELAYED" : "UNKNOWN";
        });
    }
    public ServiceStatus game() {
        return local("GAME", () -> {
            jdbc.queryForList("SELECT g.id FROM tbl_game g LEFT JOIN tbl_game_mode m ON m.game_id=g.id LIMIT 1");
            // 입장 정보와 같은 저장소를 읽기만 하며 실제 입장·좌석 확보는 실행하지 않는다.
            jdbc.queryForList("SELECT id FROM tbl_gomoku_room LIMIT 1"); ping(); return "NORMAL";
        });
    }
    public ServiceStatus chat() {
        try {
            var value = chat.get("/internal/admin/chat/health", ChatHealthSnapshot.class);
            Instant now = Instant.now();
            if (value.getState() == null || !Set.of("NORMAL", "DELAYED", "DOWN", "UNKNOWN").contains(value.getState()) || value.getCheckedAt() == null
                    || value.getCheckedAt().isAfter(now.plusSeconds(5)) || value.getCheckedAt().isBefore(now.minusSeconds(90))) return status("CHAT", "UNKNOWN", now);
            return status("CHAT", value.getState(), value.getCheckedAt());
        } catch (RuntimeException ignored) { return status("CHAT", "UNKNOWN", Instant.now()); }
    }
    private ServiceStatus local(String code, java.util.function.Supplier<String> check) {
        long started = System.nanoTime(); String state;
        try { state = check.get(); if (state.equals("NORMAL") && System.nanoTime() - started >= 1_000_000_000L) state = "DELAYED"; }
        catch (RuntimeException ignored) { state = "DOWN"; }
        return status(code, state, Instant.now());
    }
    private void ping() {
        if (!"PONG".equals(redis.execute((RedisCallback<String>) connection -> connection.commands().ping()))) throw new IllegalStateException("상태 확인 실패");
    }
    private ServiceStatus status(String code, String state, Instant at) { return ServiceStatus.builder().code(code).state(state).checkedAt(at).build(); }
}
