package com.project.playground.domain.activitylog.event;

import com.project.playground.domain.activitylog.enums.ActivityResult;
import com.project.playground.domain.activitylog.enums.ActivityType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/** 요청 스레드에서 확정한 활동 정보만 담는다. 요청 DTO·비밀번호·토큰은 넣지 않는다. */
@Getter
@Builder
public class ActivityLogEvent {

    private final ActivityType type;

    private final ActivityResult result;

    private final String failureCode;

    private final Long userId;

    private final String clientIp;

    private final String userAgent;

    private final Map<String, Object> detail;

    // 요청 스레드에서 확정한 한국 시간 기준 발생 시각
    private final LocalDateTime occurredAt;

    // 트랜잭션 리스너 조건에서 성공·실패의 저장 시점을 구분한다.
    public boolean isSuccess() {
        return result == ActivityResult.SUCCESS;
    }
}
