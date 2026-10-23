package com.project.playground.domain.game.service;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;

/** 검증된 운영 문구만 일반 서비스 장애와 구분해서 공개한다. */
public class GameUnavailableException extends BusinessException {
    private final String notice;
    public GameUnavailableException(String notice) {
        super(ErrorCode.GAME_UNAVAILABLE);
        this.notice = notice == null || notice.isBlank() ? ErrorCode.GAME_UNAVAILABLE.getMessage() : notice;
    }
    public String getNotice() { return notice; }
}
