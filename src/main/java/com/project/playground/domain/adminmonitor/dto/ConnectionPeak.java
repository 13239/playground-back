package com.project.playground.domain.adminmonitor.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

/** 빈 수집 구간은 peak=null로 구분한다. 날짜·시간 단위의 최대값만 화면에 제공한다. */
@Getter @Builder
public class ConnectionPeak {
    private final Instant at;
    private final Long peak;
}
