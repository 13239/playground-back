package com.project.playground.domain.adminmonitor.service;

import com.project.playground.domain.adminmonitor.redis.SitePresenceRepository;
import com.project.playground.domain.adminmonitor.repository.ConnectionSampleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 관리자 화면 접속과 무관하게 관측값을 수집한다. 읽기-저장-조건부 제거 순서를 유지한다. */
@Component @RequiredArgsConstructor @Slf4j
public class ConnectionSampleCollector {
    private final SitePresenceRepository presence;
    private final ConnectionSampleRepository samples;
    @Scheduled(fixedDelay = 30_000, initialDelay = 1_000, scheduler = "adminMonitoringScheduler")
    public void collect() {
        try {
            var observation = presence.pending();
            if (!observation.getPending().isEmpty()) {
                samples.save(observation.getPending());
                presence.acknowledge(observation.getPending());
            }
        } catch (RuntimeException ignored) {
            // 실패한 항목은 Redis에 남겨 재수집하며 숫자·회원 정보나 연결 자격 증명을 기록하지 않는다.
            log.warn("관리자 접속 지표의 저장이 지연되고 있습니다.");
        }
    }
}
