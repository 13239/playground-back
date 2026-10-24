package com.project.playground.domain.gomoku.repository;

import com.project.playground.domain.gomoku.entity.GomokuPlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** DB에서 원자적으로 누적해 동시에 완료된 대국도 카운터 값을 덮어쓰지 않는다. */
public interface GomokuPlayerStatsRepository extends JpaRepository<GomokuPlayerStats, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO tbl_gomoku_player_stats (user_id, wins, losses, draws)
            VALUES (:userId, :wins, :losses, :draws)
            ON DUPLICATE KEY UPDATE
                wins = wins + :wins,
                losses = losses + :losses,
                draws = draws + :draws
            """, nativeQuery = true)
    void increment(@Param("userId") Long userId, @Param("wins") long wins,
                   @Param("losses") long losses, @Param("draws") long draws);
}
