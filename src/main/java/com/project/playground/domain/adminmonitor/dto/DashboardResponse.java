package com.project.playground.domain.adminmonitor.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;

/** 지표별 확인 불가를 구분하고 수집되지 않은 그래프 구간은 null을 유지한다. */
@Getter @Builder
public class DashboardResponse {
    private final String period;
    private final Instant checkedAt;
    private final String onlineState;
    private final Long onlineCount;
    private final Instant onlineCheckedAt;
    private final Long newMembers;
    private final String historyState;
    private final Instant historyCheckedAt;
    private final List<ConnectionPeak> points;
}
