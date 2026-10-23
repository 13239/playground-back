package com.project.playground.domain.game.repository;

import com.project.playground.domain.game.entity.GameGenre;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

/** 새 장르도 코드 데이터로 조회하며 비활성화해도 기존 게임 매핑은 삭제하지 않는다. */
public interface GameGenreRepository extends JpaRepository<GameGenre, Long> {
    List<GameGenre> findByCodeIn(Collection<String> codes);
    boolean existsByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GameGenre r where r.code = :code")
    Optional<GameGenre> findForUpdate(String code);
}
