package com.project.playground.auth.ratelimit;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.Getter;

/** 탈퇴의 현재 비밀번호 대입 시도를 제한하고 다음 요청까지의 대기 시간을 전달한다. */
@Getter
public class AccountWithdrawalRateLimitException extends BusinessException {
    private final long retryAfterSeconds;

    public AccountWithdrawalRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.ACCOUNT_WITHDRAWAL_RATE_LIMIT_EXCEEDED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
