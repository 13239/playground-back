package com.project.playground.domain.game.repository;

import com.project.playground.domain.game.entity.GameMode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

/** 목록의 모드를 한 번에 조회해 게임 수에 비례한 추가 조회를 방지한다. */
public interface GameModeRepository extends JpaRepository<GameMode, Long> {
    List<GameMode> findByGameIdInOrderBySortOrderAscIdAsc(Collection<Long> gameIds);
    Optional<GameMode> findByGameIdAndCode(Long gameId, String code);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select m from GameMode m where m.gameId = :gameId and m.code = :code")
    Optional<GameMode> findForPlay(Long gameId, String code);
}
