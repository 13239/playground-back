package com.project.playground.domain.adminlog.support;

import com.project.playground.domain.adminlog.dto.*;
import com.project.playground.global.exception.ErrorCode;
import java.util.*;

/** 허용한 진단 항목과 고정 한국어 문구만 내보낸다. 예외 메시지·스택·본문은 지원하지 않는다. */
public final class LoginLogDiagnosticsMapper {
    private LoginLogDiagnosticsMapper() { }
    private static final Map<String, String> STAGES = Map.ofEntries(
            Map.entry("REQUEST_RECEIVED", "인증 서버 요청 수신"), Map.entry("BFF_AUTHENTICATION", "웹 서버 신원 확인"),
            Map.entry("BROWSER_PROOF", "브라우저 요청 증명"), Map.entry("INPUT_VALIDATION", "입력 형식 검사"),
            Map.entry("CLIENT_IP", "접속 IP 확인"), Map.entry("IP_RATE_LIMIT", "IP별 로그인 횟수 검사"),
            Map.entry("ACCOUNT_LOOKUP", "DB 회원 조회"), Map.entry("ACCOUNT_RATE_LIMIT", "계정별 로그인 횟수 검사"),
            Map.entry("PASSWORD_VERIFICATION", "아이디·비밀번호 검증"), Map.entry("RATE_LIMIT_RESET", "로그인 횟수 상태 정리"),
            Map.entry("SESSION_EXPIRY", "로그인 세션 만료 설정"), Map.entry("LOGIN_RESPONSE_BUILDING", "로그인 응답 구성"),
            Map.entry("REFRESH_TOKEN_ISSUE", "갱신 토큰 발급"), Map.entry("ACCESS_TOKEN_ISSUE", "접속 토큰 발급"),
            Map.entry("PREVIOUS_SESSION_CHECK", "기존 로그인 확인"), Map.entry("RESULT_ENCRYPTION", "로그인 결과 암호화"),
            Map.entry("ACCOUNT_LOCK", "DB 계정 잠금 확인"), Map.entry("ACCOUNT_STATE_RECHECK", "DB 계정 상태 재확인"),
            Map.entry("PASSWORD_STATE_RECHECK", "인증 중 비밀번호 변경 확인"), Map.entry("ACCESS_POLICY_CHECK", "운영 접속 정책 확인"),
            Map.entry("SESSION_CREATION", "Redis 로그인 세션 저장"), Map.entry("RESULT_DECRYPTION", "저장된 로그인 결과 확인"),
            Map.entry("BACKEND_COMPLETED", "인증 서버 처리 완료"), Map.entry("BFF_ORIGIN_CHECK", "사이트 요청 출처 확인"),
            Map.entry("BFF_BROWSER_PROOF", "웹 서버의 브라우저 증명 확인"), Map.entry("BFF_INPUT_VALIDATION", "웹 서버 입력 검사"),
            Map.entry("BFF_CLIENT_IP", "웹 서버의 접속 IP 확인"), Map.entry("BFF_COOKIE_CONFIGURATION", "로그인 쿠키 설정 확인"),
            Map.entry("BFF_COOKIE_READ", "이전 로그인 쿠키 읽기"), Map.entry("BFF_BACKEND_REQUEST", "웹 서버와 인증 서버 통신"),
            Map.entry("BFF_COOKIE_WRITE", "로그인 쿠키 저장"), Map.entry("BROWSER_LOGIN_PREPARATION", "브라우저 로그인 준비"));
    private static final Map<String, String> REASONS = Map.ofEntries(
            Map.entry("PASSWORD_MISMATCH", "입력 비밀번호가 DB에 저장된 비밀번호와 일치하지 않습니다."),
            Map.entry("PASSWORD_HASH_UNREADABLE", "저장된 비밀번호의 인코딩 정책·형식을 읽거나 검증하지 못했습니다. 데이터 손상이나 서버의 암호화 라이브러리·설정을 확인하세요."),
            Map.entry("BFF_CERTIFICATE_MISSING", "인증 서버가 웹 서버의 TLS 클라이언트 인증서를 받지 못했습니다."),
            Map.entry("BFF_CERTIFICATE_VALIDATION_FAILED", "웹 서버의 인증서 유효기간·형식을 검증하지 못했습니다."),
            Map.entry("BFF_CERTIFICATE_REJECTED", "웹 서버 인증서가 허용된 인증서와 일치하지 않습니다."),
            Map.entry("BFF_DEV_SECRET_MISMATCH", "개발 환경 웹 서버와 인증 서버의 전용 인증 키가 일치하지 않거나 헤더가 잘못됐습니다."),
            Map.entry("BFF_LOOPBACK_REQUIRED", "로컬 개발 예외에 필요한 루프백 연결이 아닙니다."),
            Map.entry("ACCOUNT_NOT_FOUND", "입력 아이디로 회원을 찾지 못했습니다."),
            Map.entry("ACCOUNT_NOT_ACTIVE", "계정이 활성 상태가 아니어서 인증이 거절됐습니다."),
            Map.entry("ACCOUNT_STATE_CHANGED", "인증 이후 DB에서 계정이 없거나 사용할 수 없는 상태로 확인됐습니다."),
            Map.entry("PASSWORD_CHANGED_DURING_LOGIN", "비밀번호 인증과 세션 발급 사이에 저장된 비밀번호가 달라졌습니다."),
            Map.entry("REDIS_ACCOUNT_BLOCKED", "DB 계정 검증 후 Redis의 계정 차단 표식 때문에 세션 생성이 거절됐습니다."),
            Map.entry("REDIS_RESULT_MISSING", "Redis 세션 생성 결과를 받지 못했습니다."),
            Map.entry("REDIS_SESSION_DUPLICATE", "새 로그인 세션 ID가 기존 값과 충돌했습니다."),
            Map.entry("REDIS_SESSION_EXPIRED", "세션 생성 시 만료 시각이 지난 것으로 확인됐습니다."),
            Map.entry("RESULT_ENCRYPTION_FAILED", "로그인 결과 직렬화·암호화 처리에 실패했습니다."),
            Map.entry("RESULT_DECRYPTION_FAILED", "로그인 결과의 암호화 키·인증 문맥·직렬화 형식을 확인하지 못했습니다."),
            Map.entry("LOGIN_COMPLETED", "인증 서버에서 비밀번호 검증과 세션 발급을 완료했습니다."),
            Map.entry("INPUT_VALIDATION_FAILED", "로그인 입력값이 서버의 필수값·길이 규칙을 통과하지 못했습니다."),
            Map.entry("INPUT_JSON_INVALID", "로그인 JSON을 해석하지 못했습니다."),
            Map.entry("REQUEST_CONNECTION_FAILED", "요청 또는 응답 연결이 정상적으로 완료되지 않았습니다."),
            Map.entry("UNEXPECTED_EXCEPTION", "이 단계에서 예상하지 못한 예외가 발생했습니다. 예외 종류를 확인하세요."),
            Map.entry("UNEXPECTED_RESPONSE", "정상 로그인 결과를 확인하지 못했습니다."),
            Map.entry("SERVER_CONFIGURATION_ERROR", "웹 서버의 인증·주소·인증서·쿠키 설정을 확인하지 못했습니다."),
            Map.entry("BACKEND_UNAVAILABLE", "웹 서버가 인증 서버에 연결하지 못했습니다."),
            Map.entry("BACKEND_TIMEOUT", "인증 서버 응답을 제한 시간 안에 받지 못했습니다."),
            Map.entry("INVALID_BACKEND_RESPONSE", "인증 서버의 응답 형식을 확인하지 못했습니다."),
            Map.entry("INVALID_ORIGIN", "요청의 사이트 출처가 서버 설정과 다릅니다."),
            Map.entry("AUTH_STORAGE_UNAVAILABLE", "브라우저에서 로그인 준비에 필요한 저장소를 사용할 수 없습니다."),
            Map.entry("AUTH_LOCK_WAIT_TIMEOUT", "브라우저의 다른 로그인 처리 대기가 끝나지 않았습니다."),
            Map.entry("AUTH_REQUEST_TIMEOUT", "브라우저의 로그인 요청 처리 시간이 초과됐습니다."),
            Map.entry("NETWORK_ERROR", "브라우저가 서버 응답을 받지 못했다고 보고했습니다."),
            Map.entry("BROWSER_KEY_REQUIRED", "브라우저의 인증 키·보안 연결을 준비하지 못했습니다."));

    public static LoginLogDiagnostics map(Map<?, ?> data) {
        String id = string(data.get("attemptId"), 36);
        String stage = code(data.get("stage")), reason = code(data.get("reason"));
        if (id == null || !id.matches("[a-f0-9-]{36}") || stage == null || reason == null) return null;
        var context = new ArrayList<LogAttribute>();
        String[][] fields = {{"environment", "실행 환경"}, {"release", "배포 버전"}, {"instance", "서버 인스턴스"},
                {"httpStatus", "최종 HTTP 상태"}, {"durationMillis", "전체 처리 시간 (ms)"}, {"originalCode", "내부 오류 코드"},
                {"credentialsVerified", "비밀번호 검증 통과"}, {"accountFound", "DB 회원 확인"}, {"accountStatus", "조회 당시 계정 상태"},
                {"accountDeleted", "조회 당시 삭제 상태"}, {"bffVerified", "웹 서버 신원 검증 통과"}, {"proofRequired", "브라우저 증명 필수"},
                {"replacementRequested", "기존 로그인 교체 동의"}, {"redisOutcome", "Redis 처리 결과"}, {"database", "MySQL 연결 대상"},
                {"redis", "Redis 연결 대상·DB"}, {"javaVersion", "Java 버전"}, {"exceptionType", "예외 종류"},
                {"rootExceptionType", "최하위 예외 종류"}, {"sqlState", "SQL 상태 코드"}, {"sqlErrorCode", "DB 오류 번호"},
                {"clientReported", "브라우저가 보고한 미검증 정보"}, {"reportedAt", "웹 서버 보고 시각 (UTC)"},
                {"deliveryDelayMillis", "보고 시각과 수신 시각 차이 (ms)"}};
        for (var field : fields) {
            Object value = data.get(field[0]);
            String text = value instanceof Boolean flag ? flag ? "예" : "아니요"
                    : value instanceof Number number ? number.toString() : string(value, 200);
            if (text != null) context.add(LogAttribute.builder().label(field[1]).value(text).build());
        }
        var steps = new ArrayList<LoginLogDiagnostics.Step>();
        if (data.get("steps") instanceof List<?> items) for (var item : items.stream().limit(32).toList()) {
            if (!(item instanceof Map<?, ?> entry)) continue;
            String step = code(entry.get("stage")), outcome = code(entry.get("outcome"));
            if (step == null || outcome == null || !Set.of("SUCCESS", "FAILURE").contains(outcome) || !(entry.get("durationMillis") instanceof Number duration)) continue;
            steps.add(LoginLogDiagnostics.Step.builder().stage(step).stageName(STAGES.getOrDefault(step, step))
                    .outcome(outcome).durationMillis(Math.max(0, Math.min(86400000, duration.longValue()))).build());
        }
        String source = switch (Objects.toString(data.get("source"), "")) {
            case "BFF" -> "웹 서버"; case "BROWSER" -> "브라우저 보고 (미검증)"; default -> "인증 서버";
        };
        return LoginLogDiagnostics.builder().attemptId(id).source(source).stage(stage).stageName(STAGES.getOrDefault(stage, stage))
                .reason(reason).reasonName(reasonName(reason)).context(List.copyOf(context)).steps(List.copyOf(steps)).build();
    }
    private static String reasonName(String reason) {
        if (REASONS.containsKey(reason)) return REASONS.get(reason);
        try { return ErrorCode.valueOf(reason).getMessage(); }
        catch (IllegalArgumentException ignored) { return "이 단계에서 처리를 완료하지 못했습니다. 내부 오류 코드와 예외 종류를 확인하세요."; }
    }
    private static String code(Object value) {
        String text = string(value, 80); return text != null && text.matches("[A-Z][A-Z0-9_]{0,79}") ? text : null;
    }
    private static String string(Object value, int limit) {
        return value instanceof String text && text.length() <= limit && text.codePoints().noneMatch(Character::isISOControl) ? text : null;
    }
}
