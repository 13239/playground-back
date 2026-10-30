package com.project.playground.auth.audit;

import java.time.LocalDateTime;
import java.util.Map;

/** 비밀값이 없는 진단 결과만 저장하는 내부 재전송 형식이다. */
public record LoginAuditRecord(String eventId, LocalDateTime occurredAt, boolean success, String failureCode,
                               Long userId, String clientIp, String userAgent, Map<String, Object> detail) { }
