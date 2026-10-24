package com.project.playground.domain.adminmonitor.service;

import com.project.playground.domain.adminmonitor.redis.SitePresenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicLong;

/** 접속 지표 장애가 유효한 로그인 확인을 실패시키거나 인증 세션 수명을 바꾸지 않게 격리한다. */
@Service @RequiredArgsConstructor @Slf4j
public class SitePresenceService {
    private final SitePresenceRepository presence;
    private final AtomicLong nextWarning = new AtomicLong();
    public void confirm(long userId) {
        try { presence.confirm(userId); }
        catch (RuntimeException ignored) {
            long now = System.nanoTime(), next = nextWarning.get();
            if (now >= next && nextWarning.compareAndSet(next, now + 60_000_000_000L)) log.warn("사이트 접속 지표 수집이 지연되고 있습니다.");
        }
    }
}
