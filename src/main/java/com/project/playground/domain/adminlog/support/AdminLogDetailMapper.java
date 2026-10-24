package com.project.playground.domain.adminlog.support;

import com.project.playground.domain.adminlog.dto.*;
import com.project.playground.domain.adminlog.dto.internal.LogDetailSource;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** 저장 JSON 전체를 반환하지 않고 현재 지원하는 업무 필드만 명시적으로 복사한다. */
@Component @RequiredArgsConstructor
public class AdminLogDetailMapper {
    private final JsonMapper json;
    private static final Map<String, String> FIELDS = fields();
    private static final Map<String, String> PRIVATE_FIELDS = Map.of("email", "이메일", "phone", "연락처", "birthday", "생년월일", "gender", "성별");

    public ActivityLogDetails activity(LogDetailSource<ActivityLogSummary> source) {
        var data = read(source.getDetail());
        var attributes = new ArrayList<LogAttribute>();
        attribute(attributes, "입력 아이디", text(data.get("username"), "username", Map.of()));
        attribute(attributes, "활동 당시 닉네임", text(data.get("nickname"), "nickname", Map.of()));
        attribute(attributes, "로그인 유지", text(data.get("keepLoggedIn"), "keepLoggedIn", Map.of()));
        return ActivityLogDetails.builder().entry(source.getEntry()).userAgent(source.getUserAgent()).attributes(List.copyOf(attributes)).build();
    }
    public AuditLogDetails audit(LogDetailSource<AuditLogSummary> source, Map<String, String> roleNames) {
        var data = read(source.getDetail());
        Object before = data.get("before"), after = data.get("after");
        var changes = new ArrayList<LogChange>();
        var attributes = new ArrayList<LogAttribute>();
        String action = source.getEntry().getAction();
        if (action.equals("USER_ROLE_CHANGED") || action.equals("USER_STATUS_CHANGED")) {
            String key = action.equals("USER_ROLE_CHANGED") ? "roleCode" : "status";
            if (data.containsKey("before") || data.containsKey("after")) changes.add(LogChange.builder()
                    .label(key.equals("roleCode") ? "권한" : "계정 상태").before(text(before, key, roleNames)).after(text(after, key, roleNames)).build());
        } else {
            Map<?, ?> previous = before instanceof Map<?, ?> map ? map : Map.of();
            Map<?, ?> current = after instanceof Map<?, ?> map ? map : Map.of();
            FIELDS.forEach((key, label) -> {
                if ((previous.containsKey(key) || current.containsKey(key)) && !Objects.equals(previous.get(key), current.get(key))) {
                    changes.add(LogChange.builder().label(label).before(text(previous.get(key), key, roleNames)).after(text(current.get(key), key, roleNames)).build());
                }
            });
        }
        // 개인정보 조회는 필드 이름만 기록한다. 원문 값이나 임의 JSON 키를 읽어 내보내지 않는다.
        if (action.equals("USER_SENSITIVE_VIEWED") && data.get("fields") instanceof List<?> list) {
            var names = list.stream().filter(String.class::isInstance).map(String.class::cast).filter(PRIVATE_FIELDS::containsKey).map(PRIVATE_FIELDS::get).toList();
            if (!names.isEmpty()) attribute(attributes, "조회 항목", String.join(", ", names));
        }
        if (action.equals("SUPER_ADMIN_INITIALIZED")) attribute(attributes, "지정 권한", text(data.get("roleCode"), "roleCode", roleNames));
        return AuditLogDetails.builder().entry(source.getEntry()).changes(List.copyOf(changes)).attributes(List.copyOf(attributes)).build();
    }
    public Set<String> roleCodes(String source) {
        var data = read(source);
        var codes = new HashSet<String>();
        collectCodes(codes, data.get("before")); collectCodes(codes, data.get("after")); collectCodes(codes, data.get("roleCode"));
        return Set.copyOf(codes);
    }
    private static void collectCodes(Set<String> codes, Object value) {
        if (value instanceof String code && code.matches("[A-Z][A-Z0-9_]{0,63}")) codes.add(code);
        if (value instanceof Map<?, ?> map && map.get("exemptRoleCodes") instanceof List<?> list) list.stream().limit(1000).forEach(item -> collectCodes(codes, item));
    }
    private Map<?, ?> read(String source) {
        if (source == null) return Map.of();
        if (source.length() > 131072) throw new BusinessException(ErrorCode.ADMIN_LOG_UNAVAILABLE);
        try {
            Object value = json.readValue(source, Object.class);
            if (value instanceof Map<?, ?> map) return map;
        } catch (RuntimeException ignored) { /* 파싱 원문과 내부 예외는 운영 조회 응답에 포함하지 않는다. */ }
        throw new BusinessException(ErrorCode.ADMIN_LOG_UNAVAILABLE);
    }
    private static void attribute(List<LogAttribute> attributes, String label, String value) {
        if (value != null) attributes.add(LogAttribute.builder().label(label).value(value).build());
    }
    private static String text(Object value, String key, Map<String, String> roles) {
        String result;
        if (value == null) return null;
        if (value instanceof Boolean flag) result = flag ? "예" : "아니요";
        else if (value instanceof Number number) result = number.toString();
        else if (value instanceof String string) {
            if (key.equals("roleCode") || key.equals("exemptRoleCodes")) result = roles.containsKey(string) ? roles.get(string) + " (" + string + ")" : string;
            else if (key.equals("permissions")) {
                try { result = PermissionCode.valueOf(string).getDisplayName() + " (" + string + ")"; }
                catch (IllegalArgumentException ignored) { result = string; }
            } else if (key.equals("status")) result = switch (string) { case "ACTIVE" -> "활성"; case "INACTIVE" -> "비활성"; case "PENDING" -> "가입대기"; default -> string; };
            else if (key.equals("availability")) result = switch (string) { case "AVAILABLE" -> "이용 가능 (AVAILABLE)"; case "MAINTENANCE" -> "점검 중 (MAINTENANCE)"; case "DISABLED" -> "이용 중지 (DISABLED)"; default -> string; };
            else result = string;
        } else if (value instanceof List<?> list) {
            result = String.join(", ", list.stream().limit(1000).map(item -> text(item, key, roles)).filter(Objects::nonNull).toList());
        } else return null;
        return result.length() > 32768 ? result.substring(0, 32767) + "…" : result;
    }
    private static Map<String, String> fields() {
        String[] entries = { "code", "코드", "name", "이름", "description", "설명", "summary", "요약", "permissions", "기능 권한",
                "enabled", "사용 여부", "message", "안내 문구", "exemptRoleCodes", "접속 허용 권한", "published", "목록 공개",
                "availability", "이용 상태", "thumbnailKey", "썸네일", "genres", "장르", "minHumanPlayers", "최소 참가 인원",
                "maxHumanPlayers", "최대 참가 인원", "sortOrder", "노출 순서" };
        var result = new LinkedHashMap<String, String>();
        for (int index = 0; index < entries.length; index += 2) result.put(entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
