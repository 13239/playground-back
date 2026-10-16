package com.project.playground.auth.ratelimit;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.Getter;

/**
 * 로그인 시도 제한 초과와 재시도까지 남은 시간을 전달한다.
 */
@Getter
public class LoginRateLimitException extends BusinessException {

    // 재시도까지 기다려야 하는 시간. 초 단위의 양수로 전달한다.
    private final long retryAfterSeconds;

    public LoginRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.LOGIN_RATE_LIMIT_EXCEEDED);

        // 잘못된 대기 시간이 클라이언트에 전달되지 않도록 검증한다.
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("재시도 대기 시간은 1초 이상이어야 합니다.");
        }

        this.retryAfterSeconds = retryAfterSeconds;
    }
}
