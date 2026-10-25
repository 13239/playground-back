package com.project.playground.domain.tetris.repository;

import com.project.playground.domain.tetris.entity.TetrisRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** 계정 PK를 잠가 중복 시작·종료와 여러 탭의 이전 판 제출을 처리한다. */
public interface TetrisRunRepository extends JpaRepository<TetrisRun, Long> {
    @Modifying @Query(value = "INSERT IGNORE INTO tbl_tetris_run (user_id) VALUES (:id)", nativeQuery = true)
    void ensure(@Param("id") Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from TetrisRun r where r.userId = :id")
    Optional<TetrisRun> lock(@Param("id") Long userId);
}
