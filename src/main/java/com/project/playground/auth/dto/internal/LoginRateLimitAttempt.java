package com.project.playground.auth.dto.internal;

import lombok.Builder;
import lombok.Getter;

/** 성공한 로그인 한 건의 IP 예약만 반환하도록 계정 식별자와 무작위 예약 ID를 서버 내부에서 보관한다. */
@Getter
@Builder
public class LoginRateLimitAttempt {
    private final String accountSubject;
    private final String ipSubject;
    private final String reservationId;
}
