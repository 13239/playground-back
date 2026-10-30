package com.project.playground.domain.social;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.Getter;

/** 소셜 기능별 요청 제한은 관계·인원 한도와 구분하고 실제 재시도 대기 시간을 전달한다. */
@Getter
public class SocialRateLimitException extends BusinessException {
    private final long retryAfterSeconds;

    public SocialRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.SOCIAL_RATE_LIMIT_EXCEEDED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
