package com.project.playground.domain.gomoku.service;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.Getter;

/** 방 API의 호출 제한을 로그인 실패와 구분하고 재시도까지 남은 초를 제공한다. */
@Getter
public class RoomRateLimitException extends BusinessException {
    private final long retryAfterSeconds;

    public RoomRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.ROOM_RATE_LIMIT_EXCEEDED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
