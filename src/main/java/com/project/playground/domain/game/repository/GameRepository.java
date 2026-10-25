package com.project.playground.domain.game.repository;

import com.project.playground.domain.game.entity.Game;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;

/** 카탈로그 변경과 신규 실행은 부모 게임부터 같은 순서로 잠근다. */
public interface GameRepository extends JpaRepository<Game, Long> {
    Optional<Game> findByCode(String code);
    boolean existsByCode(String code);
    /** 구현별 개별 조회 없이 이미 등록한 코드만 한 번에 확인한다. */
    @Query("select g.code from Game g where g.code in :codes")
    Set<String> findRegisteredCodes(Collection<String> codes);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Game g where g.code = :code")
    Optional<Game> findForUpdate(String code);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select g from Game g where g.code = :code")
    Optional<Game> findForPlay(String code);
}
