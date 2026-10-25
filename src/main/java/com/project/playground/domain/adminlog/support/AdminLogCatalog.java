package com.project.playground.domain.adminlog.support;

import com.project.playground.domain.adminlog.dto.LogOptions;
import com.project.playground.domain.activitylog.enums.ActivityType;
import com.project.playground.domain.activitylog.enums.ActivityResult;
import com.project.playground.global.exception.ErrorCode;
import java.util.*;

/** 화면 문구와 필터 옵션을 한 곳에서 관리한다. 미래의 알 수 없는 작업 코드도 원래 코드로 식별한다. */
public final class AdminLogCatalog {
    private AdminLogCatalog() {}
    private static final Map<String, String> ACTIONS = ordered(
            "USER_ROLE_CHANGED", "회원 권한 변경", "USER_STATUS_CHANGED", "계정 상태 변경",
            "USER_SESSIONS_REVOKED", "강제 로그아웃", "USER_SENSITIVE_VIEWED", "개인정보 조회",
            "ROLE_CREATED", "권한 추가", "ROLE_UPDATED", "권한 설정 변경", "ROLE_DELETED", "권한 삭제",
            "ACCESS_POLICY_UPDATED", "접속 제한 변경", "ACCESS_EXEMPTIONS_UPDATED", "접속 허용 권한 변경",
            "GAME_CREATED", "게임 추가", "GAME_UPDATED", "게임 정보 변경", "GAME_AVAILABILITY_UPDATED", "게임 공개 상태 변경",
            "GAME_MODE_CREATED", "게임 모드 추가", "GAME_MODE_UPDATED", "게임 모드 수정", "GAME_MODE_AVAILABILITY_UPDATED", "게임 모드 공개 상태 변경",
            "GAME_GENRE_CREATED", "장르 추가", "GAME_GENRE_UPDATED", "장르 수정",
            // 그리드 순서 저장은 일반 정보 변경과 구분해 조회·필터링한다.
            "GAME_ORDER_UPDATED", "게임 노출 순서 변경", "GAME_GENRE_ORDER_UPDATED", "장르 노출 순서 변경",
            "SUPER_ADMIN_INITIALIZED", "슈퍼 관리자 초기 지정");
    private static final Map<String, String> TARGETS = ordered("USER", "회원", "ROLE", "권한", "ACCESS_POLICY", "운영 설정",
            "GAME", "게임", "GAME_MODE", "게임 모드", "GAME_GENRE", "장르");
    public static String action(String code) { return ACTIONS.getOrDefault(code, code); }
    public static String target(String code) { return TARGETS.getOrDefault(code, code); }
    public static String activity(String code) { return switch (code) { case "LOGIN" -> "로그인"; case "SIGN_UP" -> "회원가입"; default -> code; }; }
    public static String result(String code) { return switch (code) { case "SUCCESS" -> "성공"; case "FAILURE" -> "실패"; default -> code; }; }
    public static String failure(String code) {
        if (code == null) return null;
        try { return ErrorCode.valueOf(code).getMessage(); } catch (IllegalArgumentException ignored) { return code; }
    }
    public static LogOptions activities() {
        return LogOptions.builder().types(Arrays.stream(ActivityType.values()).map(value -> option(value.name(), activity(value.name()))).toList())
                .results(Arrays.stream(ActivityResult.values()).map(value -> option(value.name(), result(value.name()))).toList()).targets(List.of()).build();
    }
    public static LogOptions audits() { return LogOptions.builder().types(options(ACTIONS)).results(List.of()).targets(options(TARGETS)).build(); }
    private static LogOptions.Option option(String code, String label) { return LogOptions.Option.builder().code(code).label(label).build(); }
    private static List<LogOptions.Option> options(Map<String, String> source) { return source.entrySet().stream().map(item -> option(item.getKey(), item.getValue())).toList(); }
    private static Map<String, String> ordered(String... entries) {
        var result = new LinkedHashMap<String, String>();
        for (int i = 0; i < entries.length; i += 2) result.put(entries[i], entries[i + 1]);
        return Collections.unmodifiableMap(result);
    }
}
