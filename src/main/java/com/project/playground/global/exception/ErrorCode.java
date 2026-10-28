package com.project.playground.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 친구 관계·그룹 참가의 동시 변경은 인증 오류와 구분한다.
    SOCIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 또는 요청 정보를 찾을 수 없습니다."),
    SOCIAL_CONFLICT(HttpStatus.CONFLICT, "상태가 변경되었거나 지금 처리할 수 없는 요청입니다. 새로 확인해 주세요."),
    SOCIAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "현재 친구 또는 그룹 참가 권한이 없습니다."),
    SOCIAL_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "친구 요청 또는 그룹 인원 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."),
    SOCIAL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "친구 기능을 일시적으로 이용할 수 없습니다. 다시 시도해 주세요."),
    // 본인 탈퇴의 비밀번호 대입·중복 제출은 계정별로 제한한다.
    ACCOUNT_WITHDRAWAL_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "회원탈퇴 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    // 회원 운영의 버전 충돌과 본인 비활성화 금지를 인증 실패/재로그인과 구분한다.
    USER_MANAGEMENT_CHANGED(HttpStatus.CONFLICT, "회원 정보가 변경되었습니다. 최신 내용을 불러온 뒤 다시 시도해 주세요."),
    USER_SELF_OPERATION_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 로그인한 본인 계정은 비활성화하거나 탈퇴 처리할 수 없습니다."),
    USER_MANAGEMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "회원 관리 서비스를 일시적으로 이용할 수 없습니다."),

    // 요청 DTO 검증 및 JSON 역직렬화 오류
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문의 형식 또는 값이 올바르지 않습니다."),

    // 회원 관련 비즈니스 오류
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    // 가입 요청 예산은 로그인 시도·계정 잠금과 별도로 관리한다.
    SIGN_UP_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "회원가입 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    SIGN_UP_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "회원가입 서비스를 일시적으로 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 현재 비밀번호 오입력은 로그인 만료와 구분해 수정 중인 화면과 세션을 유지한다.
    CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 다르게 입력해 주세요."),
    PASSWORD_CHANGE_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "비밀번호 변경 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    // 로그 조회는 인증 만료와 구분하고 저장소·파싱 오류의 원문을 공개하지 않는다.
    ADMIN_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "로그 기록을 찾을 수 없습니다."),
    ADMIN_LOG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "로그 기록을 일시적으로 조회할 수 없습니다."),
    // 채팅 저장소의 미존재와 일시 장애를 별도로 표시한다.
    ADMIN_CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅 기록을 찾을 수 없습니다."),
    ADMIN_CHAT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "채팅 기록을 일시적으로 조회할 수 없습니다."),

    // 관리자 정책 오류는 인증 실패와 구분하며 상세 내부 정보는 반환하지 않는다.
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "역할을 찾을 수 없습니다."),
    ROLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 역할 코드입니다."),
    ROLE_PROTECTED(HttpStatus.CONFLICT, "ROOT 권한 또는 ROOT 계정은 변경하거나 삭제할 수 없습니다."),
    // 역할의 실제 사용처를 구분해 삭제 거절 팝업에서 해결할 대상을 안내한다.
    ROLE_IN_USE(HttpStatus.CONFLICT, "회원에게 배정되어 사용 중인 권한은 삭제할 수 없습니다."),
    ROLE_ACCESS_POLICY_IN_USE(HttpStatus.CONFLICT, "접속 허용 설정에서 사용 중인 권한입니다. 설정에서 해당 권한을 먼저 제외해 주세요."),
    ROLE_DEFAULT_IN_USE(HttpStatus.CONFLICT, "신규 회원가입의 기본 권한으로 사용 중이므로 삭제할 수 없습니다."),
    ROLE_PERMISSION_NOT_ASSIGNABLE(HttpStatus.BAD_REQUEST, "해당 기능 권한은 다른 역할에 배정할 수 없습니다."),
    AUTHORIZATION_CHANGED(HttpStatus.CONFLICT, "역할 또는 권한이 변경되었습니다. 다시 조회해 주세요."),
    AUTHORIZATION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "권한 정보를 일시적으로 확인할 수 없습니다."),

    // 접속 제한은 로그인 폐기와 함께 관리자 지정 안내를 전달한다. 반영 중 장애에는 재로그인을 요구하지 않는다.
    ACCESS_RESTRICTED(HttpStatus.UNAUTHORIZED, "서버 점검 중입니다. 잠시 후 다시 로그인해 주세요."),
    ACCESS_POLICY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "접속 설정을 반영하고 있습니다. 잠시 후 다시 시도해 주세요."),
    ACCESS_EXEMPTION_INVALID(HttpStatus.BAD_REQUEST, "등록된 일반 역할만 접속 허용 목록에 지정할 수 있습니다."),

    // 운영상 게임 이용 불가와 인증/저장소 장애를 서로 다른 코드로 전달한다.
    CATALOG_GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "이용할 수 있는 게임을 찾을 수 없습니다."),
    CATALOG_MODE_NOT_FOUND(HttpStatus.NOT_FOUND, "이용할 수 있는 게임 모드를 찾을 수 없습니다."),
    CATALOG_GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 게임 장르를 찾을 수 없습니다."),
    CATALOG_GENRE_DISABLED(HttpStatus.CONFLICT, "사용 중지된 장르는 새로 연결할 수 없습니다. 장르 선택을 확인해 주세요."),
    GAME_IMPLEMENTATION_UNAVAILABLE(HttpStatus.CONFLICT, "현재 지원하지 않는 게임 구현 또는 모드입니다."),
    GAME_CONFIGURATION_INVALID(HttpStatus.BAD_REQUEST, "게임 구현이 지원하는 설정 범위를 확인해 주세요."),
    // 임의 장르·그래픽·인원을 카드 라벨로 위장하는 요청은 별도 입력 오류로 거절한다.
    GAME_CARD_LABEL_INVALID(HttpStatus.BAD_REQUEST, "선택한 카드 라벨이 현재 게임 정보와 맞지 않습니다. 라벨을 다시 선택해 주세요."),
    GAME_FILTER_NOT_FOUND(HttpStatus.NOT_FOUND, "게임 필터를 찾을 수 없습니다."),
    GAME_FILTER_CONFIGURATION_INVALID(HttpStatus.BAD_REQUEST, "필터 유형과 조건을 확인해 주세요."),
    GAME_FILTER_IN_USE(HttpStatus.CONFLICT, "연결된 게임이 있어 삭제하거나 조건을 변경할 수 없습니다. 게임 연결을 먼저 해제해 주세요."),
    GAME_CATALOG_CHANGED(HttpStatus.CONFLICT, "게임 설정이 변경되었습니다. 다시 조회해 주세요."),
    GAME_CATALOG_CONFLICT(HttpStatus.CONFLICT, "이미 등록되었거나 다른 변경과 충돌하는 게임 설정입니다."),
    GAME_CATALOG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "게임 목록을 일시적으로 확인할 수 없습니다."),
    GAME_UNAVAILABLE(HttpStatus.CONFLICT, "현재 이 게임을 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    // 테트리스 기록 검증·이전 판 제출·저장소 장애는 로그인 실패와 구분한다.
    TETRIS_RUN_CHANGED(HttpStatus.CONFLICT, "이전 게임의 기록입니다. 현재 화면에서 새 게임을 시작해 주세요."),
    TETRIS_REPLAY_INVALID(HttpStatus.BAD_REQUEST, "플레이 기록을 검증하지 못했습니다."),
    TETRIS_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "테트리스 요청이 많습니다. 잠시 후 다시 시도해 주세요."),
    TETRIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "테트리스를 일시적으로 이용할 수 없습니다."),

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
    // 강퇴된 기존 페이지는 종료하되 재참가 허용 여부를 분리해 사용자에게 명확히 안내한다.
    ROOM_KICKED(HttpStatus.FORBIDDEN, "방장에 의해 강퇴되었습니다. 이 방에는 다시 입장할 수 없습니다."),
    ROOM_KICKED_REJOIN_ALLOWED(HttpStatus.FORBIDDEN, "방장에 의해 강퇴되었습니다. 이 방에 다시 참여할 수 있습니다."),
    ROOM_KICK_FORBIDDEN(HttpStatus.FORBIDDEN, "상대 강퇴는 대기 중인 방장만 할 수 있습니다."),
    ROOM_KICK_TARGET_CHANGED(HttpStatus.CONFLICT, "참가자 정보가 변경되었습니다. 확인 후 다시 시도해 주세요."),
    // 설정 변경 권한과 오래된 편집 충돌을 인증 만료·대국 오류와 구분한다.
    ROOM_SETTINGS_FORBIDDEN(HttpStatus.FORBIDDEN, "방 설정은 현재 방장만 변경할 수 있습니다."),
    ROOM_SETTINGS_CHANGED(HttpStatus.CONFLICT, "방 상태가 변경되었습니다. 최신 설정을 확인한 뒤 다시 저장해 주세요."),

    // 친구 초대는 현재 방장의 페이지와 본인에게 온 15초 초대만 허용한다.
    GOMOKU_INVITATION_FORBIDDEN(HttpStatus.FORBIDDEN, "친구 초대는 대기 중인 방장의 현재 화면에서만 보낼 수 있습니다."),
    GOMOKU_INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "확인할 수 없는 오목 초대입니다."),
    GOMOKU_INVITATION_EXPIRED(HttpStatus.CONFLICT, "초대 시간이 지났습니다. 다시 초대해 주세요."),
    GOMOKU_INVITATION_BUSY(HttpStatus.CONFLICT, "이미 응답을 기다리는 초대가 있습니다. 잠시 후 다시 초대해 주세요."),
    GOMOKU_INVITATION_TARGET_UNAVAILABLE(HttpStatus.CONFLICT, "지금은 이 친구를 초대할 수 없습니다. 접속·참가 상태를 확인해 주세요."),

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

    // 비밀번호 인증 후 다른 로그인이 확인되면 세션을 유지한 채 사용자에게 교체 동의를 요청한다.
    LOGIN_CONFIRMATION_REQUIRED(HttpStatus.CONFLICT, "이미 다른 곳에서 로그인되어 있습니다. 기존 로그인을 종료하고 계속할지 선택해 주세요."),

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
