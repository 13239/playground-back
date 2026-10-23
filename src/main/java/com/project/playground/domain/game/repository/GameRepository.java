package com.project.playground.domain.game.repository;

import com.project.playground.domain.game.entity.Game;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

/** 카탈로그 변경과 신규 실행은 부모 게임부터 같은 순서로 잠근다. */
public interface GameRepository extends JpaRepository<Game, Long> {
    Optional<Game> findByCode(String code);
    boolean existsByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Game g where g.code = :code")
    Optional<Game> findForUpdate(String code);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select g from Game g where g.code = :code")
    Optional<Game> findForPlay(String code);
}
