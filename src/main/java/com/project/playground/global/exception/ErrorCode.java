package com.project.playground.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 요청 DTO 검증 및 JSON 역직렬화 오류
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문의 형식 또는 값이 올바르지 않습니다."),

    // 회원 관련 비즈니스 오류
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 로그인 및 세션 관련 비즈니스 오류
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    INVALID_SESSION(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 로그인 세션입니다."),

    // 로그인 시도 제한 초과. 계정 또는 IP 중 제한된 대상을 응답으로 구분하지 않는다.
    LOGIN_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // DB 또는 Redis 접근 실패로 로그인 제한을 확인할 수 없는 경우
    AUTH_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "로그인 서비스를 일시적으로 이용할 수 없습니다. 잠시 후 다시 시도해주세요."),

    // 보안 필터에서 발생하는 인증 및 접근 권한 오류
    // Bearer 토큰 전달 방식 등 인증 요청 자체가 잘못된 경우는 400으로 처리한다.
    INVALID_AUTHENTICATION_REQUEST(HttpStatus.BAD_REQUEST, "인증 요청 형식이 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 Access Token입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;
}
