package com.project.playground.auth.audit;

import com.project.playground.domain.activitylog.entity.ActivityLog;
import com.project.playground.domain.activitylog.enums.ActivityResult;
import com.project.playground.domain.activitylog.enums.ActivityType;
import com.project.playground.domain.activitylog.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 요청 트랜잭션과 분리하고 재전송은 이벤트 ID의 고유 제약으로 중복 저장하지 않는다. */
@Service @RequiredArgsConstructor
public class LoginAuditStore {
    private final ActivityLogRepository repository;
    private final JsonMapper json;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(LoginAuditRecord record) {
        record = LoginAuditSanitizer.record(record);
        if (repository.existsByLoginAuditEventId(record.eventId())) return;
        repository.saveAndFlush(ActivityLog.builder().type(ActivityType.LOGIN)
                .result(record.success() ? ActivityResult.SUCCESS : ActivityResult.FAILURE)
                .failureCode(record.failureCode()).userId(record.userId()).clientIp(record.clientIp())
                .userAgent(record.userAgent()).detail(json.writeValueAsString(record.detail()))
                .occurredAt(record.occurredAt()).loginAuditEventId(record.eventId()).build());
    }
}
