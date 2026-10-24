package com.project.playground.domain.adminmonitor.redis;

import com.project.playground.domain.adminmonitor.dto.PresenceObservation;
import com.project.playground.domain.adminmonitor.dto.PresenceObservation.PendingPeak;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

/** 홈 전용 채팅 접속자와 분리된 사이트 전체 인증 접속 지표다. Redis 시계·한 스크립트로 중복을 제거한다. */
@Repository @RequiredArgsConstructor
public class SitePresenceRepository {
    private static final List<String> KEYS = List.of("site:presence:{site-online}:users", "site:presence:{site-online}:started",
            "site:presence:{site-online}:peaks", "site:presence:{site-online}:pending", "site:presence:{site-online}:last-observed");
    private static final RedisScript<List> OBSERVE = RedisScript.of(new ClassPathResource("redis/monitoring/observe-presence.lua"), List.class);
    private static final RedisScript<Long> ACK = RedisScript.of(new ClassPathResource("redis/monitoring/ack-presence.lua"), Long.class);
    private final StringRedisTemplate redis;

    public void confirm(long userId) { observe(Long.toString(userId), 0); }
    public PresenceObservation current() { return observe("", 0); }
    public PresenceObservation pending() { return observe("", 120); }
    public void acknowledge(List<PendingPeak> peaks) {
        if (peaks.isEmpty()) return;
        List<String> args = new ArrayList<>(peaks.size() * 2);
        for (var peak : peaks) { args.add(peak.getBucket()); args.add(peak.getValue()); }
        redis.execute(ACK, List.of(KEYS.get(2), KEYS.get(3)), args.toArray());
    }
    private PresenceObservation observe(String userId, int limit) {
        List<?> values = redis.execute(OBSERVE, KEYS, userId, Integer.toString(limit));
        if (values == null || values.size() < 3 || (values.size() - 3) % 2 != 0) throw new IllegalStateException("접속 집계 응답 오류");
        List<PendingPeak> peaks = new ArrayList<>();
        for (int index = 3; index < values.size(); index += 2) {
            String bucket = String.valueOf(values.get(index)), value = String.valueOf(values.get(index + 1));
            String[] fields = value.split(":", -1);
            if (fields.length != 2) throw new IllegalStateException("접속 관측값 오류");
            peaks.add(PendingPeak.builder().bucket(bucket).value(value).sampledAt(Instant.ofEpochMilli(Long.parseLong(bucket)))
                    .peak(Long.parseLong(fields[0])).observedAt(Instant.ofEpochMilli(Long.parseLong(fields[1]))).build());
        }
        boolean ready = "READY".equals(String.valueOf(values.get(1)));
        return PresenceObservation.builder().checkedAt(Instant.ofEpochMilli(Long.parseLong(String.valueOf(values.getFirst()))))
                .ready(ready).count(ready ? Long.valueOf(String.valueOf(values.get(2))) : null).pending(List.copyOf(peaks)).build();
    }
}
