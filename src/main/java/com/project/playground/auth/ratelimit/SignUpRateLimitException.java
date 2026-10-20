package com.project.playground.auth.ratelimit;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.Getter;

/** 회원가입 요청 제한과 초 단위 재시도 시간을 전달하며 로그인 실패 카운터와 분리한다. */
@Getter
public class SignUpRateLimitException extends BusinessException {

    private final long retryAfterSeconds;

    public SignUpRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.SIGN_UP_RATE_LIMIT_EXCEEDED);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("가입 재시도 대기 시간은 1초 이상이어야 합니다.");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
