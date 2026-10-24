package com.project.playground.domain.adminmonitor.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;

/** 로그인·게임·채팅의 고정된 세 상태만 반환한다. */
@Getter @Builder
public class ServiceStatusResponse {
    private final Instant checkedAt;
    private final List<ServiceStatus> services;
}
