package com.project.playground.domain.adminmonitor.service;

import com.project.playground.domain.adminmonitor.dto.*;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/** 관리자 수만큼 점검을 반복하지 않는다. 주기 확인 결과를 권한 검사 후 읽고 오래된 결과는 확인 불가로 돌린다. */
@Service @RequiredArgsConstructor
public class AdminServiceStatusService {
    private final AdminAuthorizationService authorization;
    private final ServiceStatusProbe probes;
    private volatile List<ServiceStatus> latest = Stream.of("LOGIN", "GAME", "CHAT")
            .map(code -> ServiceStatus.builder().code(code).state("UNKNOWN").build()).toList();

    @Scheduled(fixedDelay = 30_000, initialDelay = 1_000, scheduler = "adminMonitoringScheduler")
    public void check() { latest = List.of(probes.login(), probes.game(), probes.chat()); }

    public ServiceStatusResponse snapshot(Long actor) {
        authorization.require(actor, PermissionCode.SERVICE_STATUS_READ);
        Instant now = Instant.now();
        return ServiceStatusResponse.builder().checkedAt(now).services(latest.stream().map(status ->
                status.getCheckedAt() == null || status.getCheckedAt().isBefore(now.minusSeconds(90))
                        ? ServiceStatus.builder().code(status.getCode()).state("UNKNOWN").checkedAt(status.getCheckedAt()).build() : status).toList()).build();
    }
}
