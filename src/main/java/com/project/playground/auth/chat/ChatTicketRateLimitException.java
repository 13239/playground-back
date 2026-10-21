package com.project.playground.auth.chat;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.Getter;

/** 발급 횟수 또는 대기 증명 상한을 초과하면 재접속 폭주를 막기 위한 대기 시간을 제공한다. */
@Getter
public class ChatTicketRateLimitException extends BusinessException {

    private final long retryAfterSeconds;

    public ChatTicketRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.CHAT_TICKET_RATE_LIMIT_EXCEEDED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
