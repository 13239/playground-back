package com.project.playground.domain.gomoku.repository;

import com.project.playground.domain.gomoku.entity.GomokuRoomInvitation;
import com.project.playground.domain.gomoku.entity.GomokuRoomInvitation.State;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 수신자·방 고유 제약에 더해 모든 변경은 회원 → 방 → 초대 순서로 잠근다. */
public interface GomokuRoomInvitationRepository extends JpaRepository<GomokuRoomInvitation, Long> {
    Optional<GomokuRoomInvitation> findByPublicId(String publicId);

    // 잠금 순서 확인용 투영만 먼저 읽어, 잠금 대기 전 엔티티의 옛 상태를 재사용하지 않는다.
    @Query("select i.room.id as roomId, i.inviter.id as inviterId, i.target.id as targetId from GomokuRoomInvitation i where i.publicId = :publicId")
    Optional<Identity> identity(String publicId);

    interface Identity {
        Long getRoomId();
        Long getInviterId();
        Long getTargetId();
    }
    Optional<GomokuRoomInvitation> findByInviterIdAndRequestId(Long inviterId, String requestId);
    boolean existsByTargetIdAndState(Long targetId, State state);
    List<GomokuRoomInvitation> findByRoomIdAndState(Long roomId, State state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from GomokuRoomInvitation i where i.publicId = :publicId")
    Optional<GomokuRoomInvitation> findForUpdate(String publicId);

    // 공개 스냅샷에는 본인이 보내거나 받은 유효한 초대만 제공한다.
    @Query("select i from GomokuRoomInvitation i join fetch i.room join fetch i.inviter join fetch i.target "
            + "where (i.inviter.id = :userId or i.target.id = :userId) and i.state = :state "
            + "and i.expiresAt > :now order by i.id")
    List<GomokuRoomInvitation> findVisible(Long userId, State state, Instant now, Pageable page);

    // 시작·Redis 재구독 때만 미처리 초대를 복구하며 평상시에는 DB 폴링을 추가하지 않는다.
    @Query("select i from GomokuRoomInvitation i join fetch i.room where i.state = :state and i.id > :afterId order by i.id")
    List<GomokuRoomInvitation> findPendingAfter(State state, Long afterId, Pageable page);
}
