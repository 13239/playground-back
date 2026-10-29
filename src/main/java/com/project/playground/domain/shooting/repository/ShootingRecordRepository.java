package com.project.playground.domain.shooting.repository;

import com.project.playground.domain.shooting.entity.ShootingRecord;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 모드별 회원 최고 기록을 한 번만 노출하며 스테이지 내림차순·누적 전투 시간 오름차순으로 정렬한다. */
public interface ShootingRecordRepository extends JpaRepository<ShootingRecord, String> {
    interface RankingRow {
        Long getUserId();

        String getNickname();

        String getTeammate();

        int getStage();

        long getCombatMillis();
    }

    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO tbl_shooting_record(id,run_id,user_id,mode,stage,combat_millis,finished,created_at,achieved_at)
                    VALUES(:id,:run,:user,:mode,0,0,FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE id=id
                    """,
            nativeQuery = true)
    void ensure(
            @Param("id") String id,
            @Param("run") String run,
            @Param("user") Long user,
            @Param("mode") String mode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ShootingRecord r where r.id=:id")
    Optional<ShootingRecord> lock(@Param("id") String id);

    @Query(
            value =
                    """
                    SELECT userId,nickname,teammate,stage,combatMillis FROM (
                      SELECT r.user_id AS userId,u.nickname,r.teammate,r.stage,r.combat_millis AS combatMillis,
                        ROW_NUMBER() OVER(PARTITION BY r.user_id ORDER BY r.stage DESC,r.combat_millis,r.achieved_at,r.id) AS position
                      FROM tbl_shooting_record r JOIN tbl_user u ON u.id=r.user_id
                      WHERE r.mode=:mode AND r.stage>0 AND u.status='ACTIVE' AND u.deleted_at IS NULL
                    ) ranked WHERE position=1 ORDER BY stage DESC,combatMillis,userId LIMIT 10 OFFSET :offset
                    """,
            nativeQuery = true)
    List<RankingRow> ranking(@Param("mode") String mode, @Param("offset") int offset);

    @Query(
            value =
                    """
                    SELECT COUNT(DISTINCT r.user_id) FROM tbl_shooting_record r JOIN tbl_user u ON u.id=r.user_id
                    WHERE r.mode=:mode AND r.stage>0 AND u.status='ACTIVE' AND u.deleted_at IS NULL
                    """,
            nativeQuery = true)
    long rankedCount(@Param("mode") String mode);

    Optional<ShootingRecord> findFirstByUserIdAndModeOrderByStageDescCombatMillisAsc(
            Long userId, String mode);
}
