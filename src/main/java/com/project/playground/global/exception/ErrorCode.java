package com.project.playground.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
