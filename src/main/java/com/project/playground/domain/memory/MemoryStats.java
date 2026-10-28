package com.project.playground.domain.memory;

import lombok.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 완료 트랜잭션 안에서만 최고 기록·승패를 갱신한다. 2인 랭킹과 무승부 열은 만들지 않는다. */
@Repository @RequiredArgsConstructor
public class MemoryStats {
    private final JdbcClient jdbc;
    @Getter @Builder public static class Own {
        private final Long millis;
        private final int attempts;
        private final long wins;
        private final long losses;
    }
    public Own own(Long user) {
        return jdbc.sql("SELECT best_millis,attempts,wins,losses FROM tbl_memory_stats WHERE user_id=?").param(user)
            .query((r,n) -> Own.builder().millis(r.getObject("best_millis", Long.class)).attempts(r.getInt("attempts"))
                .wins(r.getLong("wins")).losses(r.getLong("losses")).build()).optional()
            .orElseGet(() -> Own.builder().build());
    }
    public void completed(Long user, long millis, int attempts, Instant now) {
        // MySQL 8.4의 입력 행 별칭을 사용한다. 비교가 끝나기 전 best_millis를 변경하지 않는다.
        jdbc.sql("""
            INSERT INTO tbl_memory_stats(user_id,best_millis,attempts,achieved_at) VALUES(?,?,?,?) AS incoming
            ON DUPLICATE KEY UPDATE
            achieved_at=IF(tbl_memory_stats.best_millis IS NULL OR incoming.best_millis<tbl_memory_stats.best_millis OR (incoming.best_millis=tbl_memory_stats.best_millis AND incoming.attempts<tbl_memory_stats.attempts),incoming.achieved_at,tbl_memory_stats.achieved_at),
            attempts=IF(tbl_memory_stats.best_millis IS NULL OR incoming.best_millis<tbl_memory_stats.best_millis OR (incoming.best_millis=tbl_memory_stats.best_millis AND incoming.attempts<tbl_memory_stats.attempts),incoming.attempts,tbl_memory_stats.attempts),
            best_millis=IF(tbl_memory_stats.best_millis IS NULL,incoming.best_millis,LEAST(tbl_memory_stats.best_millis,incoming.best_millis))
            """).params(user,millis,attempts,Timestamp.from(now)).update();
    }
    public void result(Long user, boolean won) {
        jdbc.sql("""
            INSERT INTO tbl_memory_stats(user_id,wins,losses) VALUES(?,?,?) AS incoming
            ON DUPLICATE KEY UPDATE wins=tbl_memory_stats.wins+incoming.wins,losses=tbl_memory_stats.losses+incoming.losses
            """).params(user,won?1:0,won?0:1).update();
    }
    private static final String ELIGIBLE = " FROM tbl_memory_stats s JOIN tbl_user u ON u.id=s.user_id WHERE s.best_millis IS NOT NULL AND u.status='ACTIVE' AND u.deleted_at IS NULL ";
    public long count() { return jdbc.sql("SELECT COUNT(*)"+ELIGIBLE).query(Long.class).single(); }
    public long rank(long millis, int attempts) {
        return jdbc.sql("SELECT COUNT(*)+1"+ELIGIBLE+"AND (s.best_millis<? OR (s.best_millis=? AND s.attempts<?))")
            .params(millis,millis,attempts).query(Long.class).single();
    }
    public List<MemoryDtos.Ranking> ranking(Long user, int page) {
        return jdbc.sql("""
            SELECT * FROM (SELECT s.user_id,u.nickname,s.best_millis,s.attempts,s.achieved_at,
            RANK() OVER(ORDER BY s.best_millis,s.attempts) AS place
            """+ELIGIBLE+") ranked ORDER BY best_millis,attempts,user_id LIMIT 10 OFFSET ?")
            .param((page-1)*10).query((r,n)->MemoryDtos.Ranking.builder().rank(r.getLong("place"))
                .nickname(r.getString("nickname")).millis(r.getLong("best_millis")).attempts(r.getInt("attempts"))
                .achievedAt(r.getTimestamp("achieved_at").toInstant()).self(user.equals(r.getLong("user_id"))).build()).list();
    }
}
