package com.project.playground.domain.gomoku.repository;

import com.project.playground.domain.gomoku.entity.GomokuMatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

/** 착수·시각 변경은 항상 방 잠금 뒤 수행한다. 예약 복구는 시작·Redis 재접속 시에만 작은 배치로 조회한다. */
public interface GomokuMatchRepository extends JpaRepository<GomokuMatch, Long> {
    Optional<GomokuMatch> findFirstByRoomIdOrderByIdDesc(Long roomId);
    // 커서로 복구 배치를 나눠 진행 중인 판 전체를 한 번에 메모리로 읽지 않는다.
    @Query("select m from GomokuMatch m where m.phase <> 'FINISHED' and m.id > :afterId order by m.id")
    List<GomokuMatch> findActiveAfter(Long afterId, Pageable pageable);
}
