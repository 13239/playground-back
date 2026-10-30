package com.project.playground.auth.audit;

import java.util.*;

/** 저장 직전에도 진단 필드를 다시 선택해 미래 호출 코드가 요청 DTO를 잘못 넘겨도 비밀값을 버린다. */
public final class LoginAuditSanitizer {
    private LoginAuditSanitizer() { }
    private static final Map<String, Integer> TEXT = Map.ofEntries(
            Map.entry("attemptId", 36), Map.entry("source", 20), Map.entry("stage", 80), Map.entry("reason", 80),
            Map.entry("environment", 50), Map.entry("release", 128), Map.entry("instance", 180), Map.entry("javaVersion", 180),
            Map.entry("username", 50), Map.entry("nickname", 50), Map.entry("accountStatus", 30), Map.entry("redisOutcome", 80),
            Map.entry("database", 200), Map.entry("redis", 200), Map.entry("originalCode", 80),
            Map.entry("exceptionType", 200), Map.entry("rootExceptionType", 200), Map.entry("sqlState", 5), Map.entry("reportedAt", 40));
    private static final Set<String> FLAGS = Set.of("keepLoggedIn", "accountFound", "accountDeleted", "credentialsVerified",
            "bffVerified", "proofRequired", "replacementRequested", "clientReported");
    private static final Set<String> NUMBERS = Set.of("schemaVersion", "durationMillis", "httpStatus", "sqlErrorCode", "deliveryDelayMillis");

    public static LoginAuditRecord record(LoginAuditRecord record) {
        var clean = new LinkedHashMap<String, Object>();
        record.detail().forEach((key, value) -> {
            if (TEXT.containsKey(key) && value instanceof String text && text.codePointCount(0, text.length()) <= TEXT.get(key)
                    && text.codePoints().noneMatch(Character::isISOControl)) clean.put(key, text);
            else if (FLAGS.contains(key) && value instanceof Boolean) clean.put(key, value);
            else if (NUMBERS.contains(key) && value instanceof Number number) clean.put(key, number.longValue());
        });
        if (record.detail().get("steps") instanceof List<?> steps) {
            var selected = new ArrayList<Map<String, Object>>();
            for (var value : steps.stream().limit(32).toList()) {
                if (!(value instanceof Map<?, ?> step) || !(step.get("stage") instanceof String stage) || !stage.matches("[A-Z][A-Z0-9_]{0,79}")
                        || !(step.get("outcome") instanceof String outcome) || !Set.of("SUCCESS", "FAILURE").contains(outcome)
                        || !(step.get("durationMillis") instanceof Number duration)) continue;
                selected.add(Map.of("stage", stage, "outcome", outcome, "durationMillis", Math.max(0, Math.min(86400000, duration.longValue()))));
            }
            clean.put("steps", List.copyOf(selected));
        }
        return new LoginAuditRecord(UUID.fromString(record.eventId()).toString(), record.occurredAt(), record.success(),
                record.failureCode() != null && record.failureCode().matches("[A-Z][A-Z0-9_]{0,49}") ? record.failureCode() : null,
                record.userId(), text(record.clientIp(), 45), text(record.userAgent(), 255), Map.copyOf(clean));
    }
    private static String text(String value, int max) {
        return value != null && value.length() <= max && value.codePoints().noneMatch(Character::isISOControl) ? value : null;
    }
}
