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
    // 가입 요청 예산은 로그인 시도·계정 잠금과 별도로 관리한다.
    SIGN_UP_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "회원가입 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    SIGN_UP_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "회원가입 서비스를 일시적으로 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 오목 방은 계정당 하나만 열고 소유자만 빈 대기방을 닫을 수 있다.
    ROOM_ALREADY_HOSTING(HttpStatus.CONFLICT, "이미 만든 방이 있습니다. 목록에서 내 방을 확인해 주세요."),
    ROOM_REQUEST_EXPIRED(HttpStatus.CONFLICT, "이미 닫힌 방의 요청입니다. 새로 방을 만들어 주세요."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
    ROOM_CANNOT_CLOSE(HttpStatus.CONFLICT, "다른 참가자가 있거나 대국 중인 방은 닫을 수 없습니다."),
    ROOM_CODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "방을 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),
    ROOM_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "방 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    ROOM_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "오목 서비스를 일시적으로 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    // 대기실 상태 충돌은 로그인 만료와 구분하고 다른 계정의 방 정보를 공개하지 않는다.
    // 계정당 한 대국·한 탭만 활성화한다. 새로고침은 같은 탭 식별자로 이어받는다.
    ROOM_PAGE_IN_USE(HttpStatus.CONFLICT, "다른 탭에서 이 대국을 이용하고 있습니다. 기존 탭에서 나간 뒤 다시 시도해 주세요."),
    ROOM_ALREADY_JOINED(HttpStatus.CONFLICT, "참여 중인 방이 있습니다. 기존 방에서 나간 뒤 입장해 주세요."),
    ROOM_FULL(HttpStatus.CONFLICT, "방이 가득 찼습니다. 다른 방을 선택해 주세요."),
    ROOM_CLOSED(HttpStatus.GONE, "이미 닫힌 방입니다. 다른 방을 선택해 주세요."),
    ROOM_NOT_JOINED(HttpStatus.CONFLICT, "이 방의 참가자가 아닙니다. 목록에서 다시 입장해 주세요."),
    ROOM_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "비공개 방 비밀번호를 입력해 주세요."),
    ROOM_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "방 비밀번호가 올바르지 않습니다."),
    ROOM_NOT_FULL(HttpStatus.CONFLICT, "상대가 입장한 뒤 준비해 주세요."),

    // 대국의 늦은 명령과 금수를 구분해 정상 로그인이나 진행 중인 판을 폐기하지 않는다.
    GAME_STATE_CHANGED(HttpStatus.CONFLICT, "대국 상태가 변경되었습니다. 현재 판을 확인하고 다시 시도해 주세요."),
    GAME_NOT_YOUR_TURN(HttpStatus.CONFLICT, "상대의 차례입니다."),
    GAME_POSITION_OCCUPIED(HttpStatus.CONFLICT, "이미 돌이 놓인 위치입니다."),
    GAME_FORBIDDEN_MOVE(HttpStatus.CONFLICT, "흑돌의 3·3, 4·4, 장목 금수에는 착수할 수 없습니다."),
    GAME_UNDO_UNAVAILABLE(HttpStatus.CONFLICT, "현재 무르기를 요청하거나 응답할 수 없습니다."),

    // 로그인 및 세션 관련 비즈니스 오류
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    // 검증된 이전 세대 토큰의 재사용을 감지하여 해당 로그인 세션을 폐기한 경우
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "인증 정보 재사용이 감지되어 로그인 세션을 종료했습니다. 다시 로그인해 주세요."),
    // 브라우저 증명 실패는 요청만 거절하며 토큰 회전·세션 폐기보다 먼저 처리한다.
    BROWSER_KEY_REQUIRED(HttpStatus.UNAUTHORIZED, "이 브라우저의 로그인 키를 확인할 수 없습니다. 다시 로그인해 주세요."),
    BROWSER_PROOF_INVALID(HttpStatus.FORBIDDEN, "요청의 브라우저 인증 정보를 확인할 수 없습니다."),
    BROWSER_PROOF_EXPIRED(HttpStatus.FORBIDDEN, "요청 인증 정보가 만료되었습니다. 다시 시도해 주세요."),
    BROWSER_PROOF_REPLAYED(HttpStatus.FORBIDDEN, "이미 처리한 요청 인증 정보입니다. 다시 시도해 주세요."),
    BROWSER_PROOF_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    BFF_AUTHENTICATION_REQUIRED(HttpStatus.FORBIDDEN, "허용되지 않은 서버 연결입니다."),
    INVALID_SESSION(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 로그인 세션입니다."),

    // 채팅 접속 증명은 로그인 예산과 별도로 제한하고 내부 연결 오류와 구분한다.
    CHAT_TICKET_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "채팅 접속 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    CHAT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "채팅 서비스를 일시적으로 이용할 수 없습니다."),

    // 같은 이전 세션으로 다른 로그인 조건을 동시에 적용하거나 이미 다음 인증 변경이 끝난 경우
    LOGIN_SESSION_CHANGED(HttpStatus.CONFLICT, "다른 요청에서 로그인 상태가 변경되었습니다. 다시 로그인해 주세요."),

    // 로그인 시도 제한 초과. 계정 또는 IP 중 제한된 대상을 응답으로 구분하지 않는다.
    LOGIN_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // 인증 처리 중 DB·Redis 연결 실패 또는 시간 초과로 로그인·세션 상태를 확인할 수 없는 경우
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
