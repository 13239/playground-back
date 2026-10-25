package com.project.playground.domain.game.repository;

import com.project.playground.domain.game.entity.CatalogOrderState;
import com.project.playground.domain.game.enums.CatalogOrderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

/** 순서 조회는 공유 잠금, 순서 저장·신규 등록은 쓰기 잠금으로 동일한 목록 경계를 사용한다. */
public interface CatalogOrderStateRepository extends JpaRepository<CatalogOrderState, CatalogOrderType> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from CatalogOrderState s where s.type = :type")
    Optional<CatalogOrderState> findForRead(CatalogOrderType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CatalogOrderState s where s.type = :type")
    Optional<CatalogOrderState> findForUpdate(CatalogOrderType type);
}
