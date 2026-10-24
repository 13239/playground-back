package com.project.playground.domain.activitylog.entity;

import com.project.playground.domain.activitylog.enums.ActivityResult;
import com.project.playground.domain.activitylog.enums.ActivityType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/** 추가만 허용하는 활동 로그다. 수정·삭제 시각이 없으므로 TimeEntity를 상속하지 않는다. */
@Entity
@Immutable
@Table(name = "tbl_activity_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40, columnDefinition = "varchar(40)")
    private ActivityType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10)")
    private ActivityResult result;

    // 실패 시 응답에 사용한 ErrorCode 이름
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    // 미등록 아이디의 로그인 실패처럼 회원을 특정할 수 없으면 null
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    // 허용한 키만 담은 JSON 문자열
    @Column(columnDefinition = "json")
    private String detail;

    // 입력 당시 식별자의 검색 전용 가상 컬럼이다. 기존 로그 저장 입력·스냅샷은 변경하지 않는다.
    @Column(name = "search_username", length = 50, insertable = false, updatable = false)
    private String searchUsername;

    @Column(name = "search_nickname", length = 50, insertable = false, updatable = false)
    private String searchNickname;

    // 비동기 저장 시각이 아닌 요청에서 활동이 발생한 시각. 회원 생성 시각과 같은 한국 시간으로 저장한다.
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Builder
    public ActivityLog(ActivityType type, ActivityResult result, String failureCode, Long userId,
                       String clientIp, String userAgent, String detail, LocalDateTime occurredAt) {
        this.type = type;
        this.result = result;
        this.failureCode = failureCode;
        this.userId = userId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }
}
