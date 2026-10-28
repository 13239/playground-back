package com.project.playground.domain.memory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** 입장·카드 선택·시계가 모두 같은 방 행을 잠가 정원과 점수가 중복 갱신되지 않게 한다. */
public interface MemoryRoomRepository extends JpaRepository<MemoryRoom, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from MemoryRoom r where r.id = :id")
    Optional<MemoryRoom> lock(Long id);
    Optional<MemoryRoom> findByRequestId(String requestId);
    Optional<MemoryRoom> findByCode(String code);
    @Query("select r from MemoryRoom r where r.closed = false and (r.hostId = :userId or r.guestId = :userId) order by r.id desc")
    List<MemoryRoom> current(Long userId);
    @Query("select r from MemoryRoom r where r.closed = false and r.solo = false and r.board.phase = 'WAITING' order by r.id desc")
    List<MemoryRoom> waiting(Pageable pageable);
    @Query("select count(r) from MemoryRoom r where r.closed = false and r.solo = false and r.board.phase = 'WAITING'")
    long waitingCount();
    @Query("select r from MemoryRoom r where r.closed = false and r.id > :after order by r.id")
    List<MemoryRoom> recover(Long after, Pageable pageable);
}
