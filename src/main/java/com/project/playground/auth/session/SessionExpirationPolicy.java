package com.project.playground.auth.session;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Getter
@Component
public class SessionExpirationPolicy {

    private final Duration rememberTtl;

    private final Duration idleTtl;

    public SessionExpirationPolicy(
            @Value("${auth.session.remember-ttl}") Duration rememberTtl,
            @Value("${auth.session.idle-ttl}") Duration idleTtl
    ) {
        if (rememberTtl.isNegative() || rememberTtl.isZero() || idleTtl.isNegative() || idleTtl.isZero()) {
            throw new IllegalArgumentException("세션 만료 시간은 0보다 커야 합니다.");
        }

        this.rememberTtl = rememberTtl;
        this.idleTtl = idleTtl;
    }

    public Instant calculateInitialExpiresAt(boolean keepLoggedIn, Instant now) {
        Duration ttl = keepLoggedIn ? rememberTtl : idleTtl;

        return now.plus(ttl);
    }
}
