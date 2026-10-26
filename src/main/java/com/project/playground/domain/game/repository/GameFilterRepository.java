package com.project.playground.domain.game.repository;

import com.project.playground.domain.game.entity.GameFilter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

/** 새 장르도 코드 데이터로 조회하며 비활성화해도 기존 게임 매핑은 삭제하지 않는다. */
public interface GameFilterRepository extends JpaRepository<GameFilter, Long> {
    Optional<GameFilter> findByCode(String code);
    List<GameFilter> findByCodeIn(Collection<String> codes);
    boolean existsByCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GameFilter r where r.code = :code")
    Optional<GameFilter> findForUpdate(String code);
    @Query("select f from GameFilter f where f.code in :codes and f.type = com.project.playground.domain.game.enums.GameFilterType.GENRE")
    List<GameFilter> findGenresByCodeIn(Collection<String> codes);
    /** 라벨 선택·기존 장르 연결을 합쳐 중복 없이 집계한다. 연결 데이터는 삭제하거나 수정하지 않는다. */
    @Query(value = """
            SELECT linked.label_code AS labelCode, COUNT(DISTINCT linked.game_id) AS gameCount
            FROM (
                SELECT label_code, game_id FROM tbl_game_card_label WHERE label_code IN :labels
                UNION ALL
                SELECT f.label_code, m.game_id FROM tbl_game_filter f
                JOIN tbl_game_genre_mapping m ON m.genre_id=f.id WHERE f.label_code IN :labels
            ) linked GROUP BY linked.label_code
            """, nativeQuery = true)
    List<LinkCount> countLinks(Collection<String> labels);
    interface LinkCount { String getLabelCode(); long getGameCount(); }
}
