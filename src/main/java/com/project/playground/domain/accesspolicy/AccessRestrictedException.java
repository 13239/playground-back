package com.project.playground.domain.accesspolicy;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;

/** 관리자 입력으로 검증한 공지 문구만 동적 공개 오류에 실어 보낸다. */
public class AccessRestrictedException extends BusinessException {
    private final String notice;
    public AccessRestrictedException(String notice) {
        super(ErrorCode.ACCESS_RESTRICTED);
        this.notice = notice;
    }
    public String getNotice() { return notice; }
}
