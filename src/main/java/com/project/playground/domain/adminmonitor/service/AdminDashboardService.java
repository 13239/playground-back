package com.project.playground.domain.adminmonitor.service;

import com.project.playground.domain.adminmonitor.dto.*;
import com.project.playground.domain.adminmonitor.redis.SitePresenceRepository;
import com.project.playground.domain.adminmonitor.repository.ConnectionSampleRepository;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** KST의 오늘/7일/30일만 조회한다. Redis·회원·추이 장애는 서로 정상 값으로 가장하지 않는다. */
@Service @RequiredArgsConstructor
public class AdminDashboardService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final AdminAuthorizationService authorization;
    private final SitePresenceRepository presence;
    private final ConnectionSampleRepository samples;

    public DashboardResponse dashboard(Long actor, String period) {
        authorization.require(actor, PermissionCode.DASHBOARD_READ);
        if (!Set.of("TODAY", "WEEK", "MONTH").contains(period)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        Instant now = Instant.now();
        LocalDate today = now.atZone(KST).toLocalDate();
        boolean hourly = period.equals("TODAY");
        LocalDate startDate = today.minusDays(period.equals("WEEK") ? 6 : period.equals("MONTH") ? 29 : 0);
        Instant start = startDate.atStartOfDay(KST).toInstant();
        String onlineState = "UNKNOWN", historyState = "READY";
        Long onlineCount = null, newMembers = null;
        Instant onlineAt = null, historyAt = null;
        try {
            var value = presence.current(); onlineAt = value.getCheckedAt(); onlineCount = value.getCount();
            onlineState = value.isReady() ? "READY" : "COLLECTING";
        } catch (RuntimeException ignored) { /* 확인 실패를 0명으로 바꾸지 않는다. */ }
        try { newMembers = samples.newMembers(today); } catch (RuntimeException ignored) { /* 회원 수만 확인 불가로 표시한다. */ }
        Map<Instant, ConnectionPeak> measured = Map.of();
        try {
            var history = samples.peaks(start, now, hourly); historyAt = history.getLastObservedAt();
            measured = history.getPoints().stream().collect(Collectors.toMap(ConnectionPeak::getAt, Function.identity()));
            if (historyAt != null && historyAt.isBefore(now.minusSeconds(90))) historyState = "DELAYED";
        }
        catch (RuntimeException ignored) { historyState = "UNKNOWN"; }
        if (historyState.equals("READY") && measured.isEmpty()) historyState = "COLLECTING";
        List<ConnectionPeak> points = new ArrayList<>();
        // 미래 시간은 생성하지 않는다. 누락 구간은 0으로 채우거나 선으로 이어 붙이지 않는다.
        Instant last = hourly ? now.truncatedTo(ChronoUnit.HOURS) : today.atStartOfDay(KST).toInstant();
        for (Instant at = start; !at.isAfter(last); at = at.plus(hourly ? 1 : 24, ChronoUnit.HOURS)) {
            points.add(ConnectionPeak.builder().at(at).peak(measured.containsKey(at) ? measured.get(at).getPeak() : null).build());
        }
        return DashboardResponse.builder().period(period).checkedAt(now).onlineState(onlineState).onlineCount(onlineCount).onlineCheckedAt(onlineAt)
                .newMembers(newMembers).historyState(historyState).historyCheckedAt(historyAt).points(List.copyOf(points)).build();
    }
}
