package com.project.playground.domain.adminaudit.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/** 중요 관리자 기록은 업무 변경과 같은 트랜잭션에서 추가하며 수정/삭제 기능을 제공하지 않는다. */
@Entity
@Immutable
@Table(name = "tbl_admin_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(name = "actor_role_code", length = 64)
    private String actorRoleCode;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
    @Column(length = 300)
    private String reason;
    @Column(nullable = false, columnDefinition = "json")
    private String detail;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Builder
    public AdminAuditLog(Long actorUserId, String actorRoleCode, String action, String targetType,
                         String targetId, String reason, String detail, LocalDateTime occurredAt) {
        this.actorUserId = actorUserId;
        this.actorRoleCode = actorRoleCode;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }
}

