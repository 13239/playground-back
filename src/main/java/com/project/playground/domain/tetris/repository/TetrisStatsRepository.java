package com.project.playground.domain.tetris.repository;

import com.project.playground.domain.tetris.entity.TetrisPlayerStats;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/** 동점은 회원 ID 순서로 고정해 번호 페이지와 내 순위가 같은 정렬을 사용한다. */
public interface TetrisStatsRepository extends JpaRepository<TetrisPlayerStats, Long> {
    interface RankingRow { Long getUserId(); String getNickname(); long getScore(); }
    interface OwnRow { long getBestScore(); long getPlays(); }
    // 원자적 SQL 갱신 직후에도 JPA 1차 캐시의 이전 통계가 응답에 섞이지 않도록 스칼라로 읽는다.
    @Query(value = "SELECT best_score AS bestScore, plays FROM tbl_tetris_player_stats WHERE user_id = :id", nativeQuery = true)
    Optional<OwnRow> own(@Param("id") Long id);
    // 서버가 완료를 확인한 판의 최초 통계 생성·최고 점수·게임 오버 1회를 한 SQL로 갱신한다.
    @Modifying @Query(value = """
        INSERT INTO tbl_tetris_player_stats (user_id, best_score, plays) VALUES (:id, :score, 1)
        ON DUPLICATE KEY UPDATE best_score = GREATEST(best_score, :score), plays = plays + 1
        """, nativeQuery = true)
    void completed(@Param("id") Long id, @Param("score") long score);
    @Query(value = """
        SELECT s.user_id AS userId, u.nickname AS nickname, s.best_score AS score
        FROM tbl_tetris_player_stats s JOIN tbl_user u ON u.id = s.user_id
        WHERE s.best_score > 0 AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
        ORDER BY s.best_score DESC, s.user_id ASC LIMIT 10 OFFSET :offset
        """, nativeQuery = true)
    List<RankingRow> ranking(@Param("offset") int offset);
    @Query(value = "SELECT COUNT(*) FROM tbl_tetris_player_stats s JOIN tbl_user u ON u.id = s.user_id WHERE s.best_score > 0 AND u.status = 'ACTIVE' AND u.deleted_at IS NULL", nativeQuery = true)
    long rankedCount();
    @Query(value = """
        SELECT COUNT(*) + 1 FROM tbl_tetris_player_stats s JOIN tbl_user u ON u.id = s.user_id
        WHERE u.status = 'ACTIVE' AND u.deleted_at IS NULL AND s.best_score > 0
        AND (s.best_score > :score OR (s.best_score = :score AND s.user_id < :id))
        """, nativeQuery = true)
    long rank(@Param("id") Long id, @Param("score") long score);
}
