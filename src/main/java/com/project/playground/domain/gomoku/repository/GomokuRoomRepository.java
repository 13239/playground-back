package com.project.playground.domain.gomoku.repository;

import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

/** 계정 잠금과 DB 고유 제약으로 중복 방 생성을 막고 닫기는 방 행 잠금으로 직렬화한다. */
public interface GomokuRoomRepository extends JpaRepository<GomokuRoom, Long> {
    Optional<GomokuRoom> findByHostIdAndStatusNot(Long hostId, RoomStatus status);
    Optional<GomokuRoom> findByCreatorIdAndCreateRequestId(Long creatorId, String requestId);
    boolean existsByInviteCode(String inviteCode);
    // 초대 코드는 조회에만 사용하고 실제 정원 검증은 방 행 잠금을 획득한 뒤 수행한다.
    Optional<GomokuRoom> findByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GomokuRoom> findForUpdateById(Long id);
}
