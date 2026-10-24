package com.project.playground.domain.adminlog.support;

import com.project.playground.global.exception.*;
import lombok.Builder;
import lombok.Getter;
import java.time.*;
import java.util.Set;

/** 한국 시각의 날짜 양끝을 포함하며 화면과 API가 같은 날짜·페이지 규칙을 사용한다. */
@Getter @Builder
public class LogSearch {
    private final LocalDateTime from;
    private final LocalDateTime until;
    private final String query;
    private final String type;
    private final String result;
    private final String targetType;
    private final int page;
    private final int size;

    public static LogSearch of(LocalDate from, LocalDate to, String query, String type, String result, String targetType, int page, int size) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate start = from == null ? today.minusDays(6) : from;
        LocalDate end = to == null ? today : to;
        if (start.isAfter(end) || start.getYear() < 1000 || !end.isBefore(LocalDate.of(9999, 12, 31))
                || page < 1 || !Set.of(10, 20, 30, 50).contains(size) || query != null && query.codePointCount(0, query.length()) > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return LogSearch.builder().from(start.atStartOfDay()).until(end.plusDays(1).atStartOfDay())
                .query(query == null || query.isBlank() ? null : query.strip()).type(type).result(result).targetType(targetType).page(page).size(size).build();
    }
    public String prefix() { return query == null ? null : query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }
    public Long memberId() {
        if (query == null || !query.matches("[1-9]\\d{0,18}")) return null;
        try { return Long.valueOf(query); } catch (NumberFormatException ignored) { return null; }
    }
}
