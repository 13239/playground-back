package com.project.playground.domain.adminaudit.service;

import com.project.playground.domain.adminaudit.entity.AdminAuditLog;
import com.project.playground.domain.adminaudit.repository.AdminAuditLogRepository;
import com.project.playground.domain.authorization.dto.response.AdminContextResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/** 비동기 활동 로그와 달리 저장 실패를 숨기지 않아 권한 변경도 함께 롤백한다. */
@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private static final ZoneId AUDIT_ZONE = ZoneId.of("Asia/Seoul");
    private final AdminAuditLogRepository audits;
    private final JsonMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(AdminContextResponse actor, String action, String targetType, String targetId,
                      String reason, Map<String, ?> detail) {
        // 호출자가 만든 명시적인 스냅샷만 직렬화한다. 엔티티/요청 DTO 전체를 기록하지 않는다.
        audits.save(AdminAuditLog.builder()
                .actorUserId(actor == null ? null : actor.getUserId())
                .actorRoleCode(actor == null ? null : actor.getRoleCode())
                .action(action).targetType(targetType).targetId(targetId)
                .reason(reason == null || reason.isBlank() ? null : reason.strip())
                .detail(json.writeValueAsString(detail))
                .occurredAt(LocalDateTime.now(AUDIT_ZONE)).build());
    }
}

