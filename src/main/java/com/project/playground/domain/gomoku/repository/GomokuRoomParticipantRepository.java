package com.project.playground.domain.gomoku.repository;

import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import java.util.List;
import java.util.Optional;

/** 최대 두 명의 실제 프로필만 조회하며 사용자 엔티티나 내부 ID를 응답으로 직렬화하지 않는다. */
public interface GomokuRoomParticipantRepository extends JpaRepository<GomokuRoomParticipant, Long> {
    @Query("select p from GomokuRoomParticipant p join fetch p.user where p.room.id = :roomId order by p.seat")
    List<GomokuRoomParticipant> findMembers(Long roomId);

    @Query("select p from GomokuRoomParticipant p join fetch p.room join fetch p.user where p.user.id = :userId")
    Optional<GomokuRoomParticipant> findCurrent(Long userId);

    // 접속 확인 인덱스로 만료 후보만 제한 조회하고 실제 삭제는 방 잠금 안에서 다시 검증한다.
    @Query("select distinct p.room.id from GomokuRoomParticipant p where p.lastSeenAt < :cutoff")
    List<Long> findExpiredRoomIds(Instant cutoff, Pageable page);

    // 종료 예약 복구는 서버 시작·Redis 재구독 때만 읽고, 평상시의 빈 테이블 폴링을 추가하지 않는다.
    @Query("select p from GomokuRoomParticipant p where p.exitDeadline is not null and p.id > :afterId order by p.id")
    List<GomokuRoomParticipant> findDepartingAfter(Long afterId, Pageable page);
}
