package com.project.playground.domain.gomoku.repository;

import com.project.playground.domain.gomoku.entity.GomokuRoomKick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 마지막 강퇴의 재참가 정책을 확인한다. 다른 방이나 계정의 참가 상태에는 영향을 주지 않는다. */
public interface GomokuRoomKickRepository extends JpaRepository<GomokuRoomKick, Long> {
    Optional<GomokuRoomKick> findByRoomIdAndUserId(Long roomId, Long userId);
}
