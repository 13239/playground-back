package com.project.playground.domain.adminmonitor.repository;

import com.project.playground.domain.adminmonitor.dto.*;
import com.project.playground.domain.adminmonitor.dto.PresenceObservation.PendingPeak;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.time.*;
import java.util.List;
import java.util.ArrayList;
import lombok.Builder;
import lombok.Getter;

/** 수치만 일괄 저장한다. SQL 커밋 뒤 Redis를 확인 제거하므로 장애·다중 인스턴스 재수집도 멱등이다. */
@Repository
public class ConnectionSampleRepository {
    private final JdbcTemplate jdbc;
    public ConnectionSampleRepository(DataSource source) { jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(3); }

    @Transactional
    public void save(List<PendingPeak> peaks) {
        // 폐기 예정 VALUES(column) 대신 MySQL의 신규 행 별칭을 사용한다.
        jdbc.batchUpdate("""
                INSERT INTO tbl_admin_connection_sample (sampled_at, peak_count, observed_at) VALUES (?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE peak_count=GREATEST(tbl_admin_connection_sample.peak_count, incoming.peak_count),
                    observed_at=GREATEST(tbl_admin_connection_sample.observed_at, incoming.observed_at)
                """, peaks, 120, (statement, peak) -> {
            statement.setObject(1, LocalDateTime.ofInstant(peak.getSampledAt(), ZoneOffset.UTC));
            statement.setLong(2, peak.getPeak());
            statement.setObject(3, LocalDateTime.ofInstant(peak.getObservedAt(), ZoneOffset.UTC));
        });
    }

    public HistoryRead peaks(Instant from, Instant until, boolean hourly) {
        // WHERE는 원본 시각 범위를 사용해 PK 인덱스를 타고, 그룹만 UTC 한 시간/하루로 접는다.
        // KST 자정은 UTC 15시다. 일별 그룹은 KST 날짜로 변환한 뒤 다시 UTC 시작 시각으로 반환한다.
        String bucket = hourly ? "CAST(DATE_FORMAT(sampled_at, '%Y-%m-%d %H:00:00') AS DATETIME)"
                : "DATE_SUB(DATE(DATE_ADD(sampled_at, INTERVAL 9 HOUR)), INTERVAL 9 HOUR)";
        return jdbc.query("SELECT " + bucket + " AS bucket, MAX(peak_count) AS peak, MAX(observed_at) AS observed FROM tbl_admin_connection_sample WHERE sampled_at >= ? AND sampled_at < ? GROUP BY bucket ORDER BY bucket",
                result -> {
                    List<ConnectionPeak> points = new ArrayList<>(); Instant latest = null;
                    while (result.next()) {
                        points.add(ConnectionPeak.builder().at(result.getObject("bucket", LocalDateTime.class).toInstant(ZoneOffset.UTC)).peak(result.getLong("peak")).build());
                        Instant observed = result.getObject("observed", LocalDateTime.class).toInstant(ZoneOffset.UTC);
                        if (latest == null || observed.isAfter(latest)) latest = observed;
                    }
                    return HistoryRead.builder().points(List.copyOf(points)).lastObservedAt(latest).build();
                },
                LocalDateTime.ofInstant(from, ZoneOffset.UTC), LocalDateTime.ofInstant(until, ZoneOffset.UTC));
    }
    public long newMembers(LocalDate today) {
        // 기존 JPA LocalDateTime 감사 시각은 JVM 시간대에 저장된다. KST 날짜 경계만 저장 시간대로 변환한다.
        ZoneId sourceZone = ZoneId.systemDefault(), kst = ZoneId.of("Asia/Seoul");
        LocalDateTime from = LocalDateTime.ofInstant(today.atStartOfDay(kst).toInstant(), sourceZone);
        LocalDateTime until = LocalDateTime.ofInstant(today.plusDays(1).atStartOfDay(kst).toInstant(), sourceZone);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tbl_user WHERE created_at >= ? AND created_at < ?", Long.class, from, until);
        return count == null ? 0 : count;
    }

    /** 조회 시각과 마지막 실제 수집 시각을 분리해 오래된 그래프를 최신으로 표시하지 않는다. */
    @Getter @Builder
    public static class HistoryRead {
        private final List<ConnectionPeak> points;
        private final Instant lastObservedAt;
    }
}
