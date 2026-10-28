package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.dto.request.GomokuInvitationRequest;
import com.project.playground.domain.gomoku.dto.response.GomokuInvitationResponse;
import com.project.playground.domain.gomoku.dto.response.GomokuInvitationResponse.Decision;
import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.entity.GomokuRoomInvitation;
import com.project.playground.domain.gomoku.entity.GomokuRoomInvitation.State;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import com.project.playground.domain.gomoku.repository.*;
import com.project.playground.domain.social.SocialFriendshipPolicy;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** 친구 원본·현재 방장 문서·좌석과 15초 기한을 같은 트랜잭션에서 검사한다. */
@Service @RequiredArgsConstructor
@Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
public class GomokuInvitationService {
    private final GomokuRoomInvitationRepository invitations;
    private final GomokuRoomRepository rooms;
    private final GomokuRoomParticipantRepository participants;
    private final GomokuRoomKickRepository kicks;
    private final UserRepository users;
    private final SocialFriendshipPolicy friendship;
    private final GomokuWaitingRoomService waiting;
    private final GomokuGameEvents events;
    private final RoomRateLimitService limits;
    private final StringRedisTemplate redis;
    private final Clock gomokuClock;

    public GomokuInvitationResponse send(Long userId, UUID sessionId, GomokuInvitationRequest request) {
        limits.invitation(userId);
        if (request.getRoomId() == null) throw failure(ErrorCode.INVALID_REQUEST_BODY);
        String targetPublicId = uuid(request.getTargetId());
        String requestId = uuid(request.getRequestId());
        String pageId = uuid(request.getPageId());
        Long targetId = users.findActiveIdByPublicId(targetPublicId)
                .orElseThrow(() -> failure(ErrorCode.GOMOKU_INVITATION_TARGET_UNAVAILABLE));
        lockUsers(userId, targetId);
        var room = lockedRoom(request.getRoomId());
        var members = participants.findMembers(room.getId());
        var host = members.stream().filter(p -> p.getUser().getId().equals(userId)).findFirst()
                .orElseThrow(() -> failure(ErrorCode.GOMOKU_INVITATION_FORBIDDEN));
        if (!room.getHost().getId().equals(userId) || !host.ownsPage(pageId)
                || !sessionId.toString().equals(host.getLoginSessionId())) {
            throw failure(ErrorCode.GOMOKU_INVITATION_FORBIDDEN);
        }
        if (room.getStatus() != RoomStatus.WAITING || members.size() != 1) throw failure(ErrorCode.ROOM_FULL);
        if (!friendship.friends(userId, targetId)) throw failure(ErrorCode.SOCIAL_ACCESS_DENIED);
        Instant now = gomokuClock.instant();
        var previous = invitations.findByInviterIdAndRequestId(userId, requestId);
        if (previous.isPresent()) {
            var same = previous.get();
            if (!same.getRoom().getId().equals(room.getId()) || !same.getTarget().getId().equals(targetId)) {
                throw failure(ErrorCode.INVALID_REQUEST_BODY);
            }
            if (same.getState() != State.PENDING || same.expired(now) || !valid(same, members, now)) {
                throw failure(ErrorCode.GOMOKU_INVITATION_EXPIRED);
            }
            return GomokuInvitationResponse.from(same);
        }
        // 한 번의 네트워크 재시도로 15초를 연장하거나 동일 상대에게 여러 팝업을 보내지 않는다.
        for (var pending : invitations.findByRoomIdAndState(room.getId(), State.PENDING)) {
            if (pending.expired(now) || !valid(pending, members, now)) finish(pending,
                    pending.expired(now) ? State.REJECTED : State.CANCELLED, now, null);
            else if (pending.getTarget().getId().equals(targetId)) return GomokuInvitationResponse.from(pending);
            else throw failure(ErrorCode.GOMOKU_INVITATION_BUSY);
        }
        invitations.flush();
        if (participants.findCurrent(targetId).isPresent()
                || invitations.existsByTargetIdAndState(targetId, State.PENDING)
                || !Boolean.TRUE.equals(redis.hasKey("social:online:" + targetPublicId))
                || kicks.findByRoomIdAndUserId(room.getId(), targetId).filter(kick -> kick.isReentryBlocked()).isPresent()) {
            throw failure(ErrorCode.GOMOKU_INVITATION_TARGET_UNAVAILABLE);
        }
        User target = users.getReferenceById(targetId);
        var invitation = invitations.saveAndFlush(GomokuRoomInvitation.builder().room(room).inviter(host.getUser())
                .target(target).requestId(requestId).hostMembershipId(host.getMembershipId()).now(now).build());
        friendship.changed(List.of(userId, targetId));
        // 기존 영속 게임 시계와 Redis 마감 전파를 재사용해 브라우저 종료·서버 재시작에도 자동 거절한다.
        events.clockChanged(room.getId(), invitation.getExpiresAt());
        return GomokuInvitationResponse.from(invitation);
    }

    public Decision respond(Long userId, String publicId, String action) {
        // 내부 호출도 오타·알 수 없는 동작을 거절하며 임의 동작을 거절 처리로 바꾸지 않는다.
        if (!"accept".equals(action) && !"reject".equals(action) && !"cancel".equals(action)) {
            throw failure(ErrorCode.INVALID_REQUEST_BODY);
        }
        limits.invitationReply(userId);
        var identity = invitations.identity(uuid(publicId))
                .orElseThrow(() -> failure(ErrorCode.GOMOKU_INVITATION_NOT_FOUND));
        boolean cancelling = "cancel".equals(action);
        if (cancelling ? !identity.getInviterId().equals(userId) : !identity.getTargetId().equals(userId)) {
            throw failure(ErrorCode.GOMOKU_INVITATION_NOT_FOUND);
        }
        lockUsers(identity.getInviterId(), identity.getTargetId());
        var room = lockedRoom(identity.getRoomId());
        var invitation = invitations.findForUpdate(publicId)
                .orElseThrow(() -> failure(ErrorCode.GOMOKU_INVITATION_NOT_FOUND));
        if (invitation.getState() == State.ACCEPTED) {
            // 수락이 먼저 확정됐으면 늦은 발신 취소·수신 거절도 실제 확정 상태를 반환한다.
            if (!"accept".equals(action)) return decision(State.ACCEPTED, room.getId());
            // 다른 탭의 중복 수락은 같은 참가만 복원하며 퇴장한 초대로 재입장시키지 않는다.
            boolean sameMembership = participants.findCurrent(userId).filter(p -> p.getRoom().getId().equals(room.getId())
                    && p.getMembershipId().equals(invitation.getAcceptedMembershipId())).isPresent();
            return decision(sameMembership ? State.ACCEPTED : State.CANCELLED, sameMembership ? room.getId() : null);
        }
        if (invitation.getState() != State.PENDING) return decision(invitation.getState(), null);
        Instant now = gomokuClock.instant();
        var members = participants.findMembers(room.getId());
        if (invitation.expired(now) || !valid(invitation, members, now)) {
            State state = invitation.expired(now) ? State.REJECTED : State.CANCELLED;
            finish(invitation, state, now, null);
            return decision(state, null);
        }
        if (!"accept".equals(action)) {
            State state = cancelling ? State.CANCELLED : State.REJECTED;
            finish(invitation, state, now, null);
            return decision(state, null);
        }
        // 비밀번호 예외는 본인의 유효한 친구 초대 승인에서만 부여한다. 정원·강퇴·중복 참가는 기존 입장 검증을 거친다.
        var joined = waiting.joinInvited(userId, room.getId());
        finish(invitation, State.ACCEPTED, now, joined.getMembershipId());
        return decision(State.ACCEPTED, room.getId());
    }

    @Transactional(readOnly = true)
    public List<GomokuInvitationResponse> visible(Long userId) {
        Instant now = gomokuClock.instant();
        return invitations.findVisible(userId, State.PENDING, now, PageRequest.of(0, 20)).stream()
                .filter(i -> valid(i, participants.findMembers(i.getRoom().getId()), now))
                .map(GomokuInvitationResponse::from).toList();
    }

    /** 같은 방 잠금 아래에서 기한이 지난 초대는 REJECTED로 확정하고 빈 방은 다시 조회하지 않는다. */
    public Instant tick(Long roomId) {
        var room = rooms.findForUpdateById(roomId);
        if (room.isEmpty()) return null;
        var pending = invitations.findByRoomIdAndState(roomId, State.PENDING);
        if (pending.isEmpty()) return null;
        var members = participants.findMembers(roomId);
        Instant now = gomokuClock.instant();
        Instant next = null;
        for (var invitation : pending) {
            if (invitation.expired(now) || !valid(invitation, members, now)) {
                finish(invitation, invitation.expired(now) ? State.REJECTED : State.CANCELLED, now, null);
            } else if (next == null || invitation.getExpiresAt().isBefore(next)) next = invitation.getExpiresAt();
        }
        return next;
    }

    private boolean valid(GomokuRoomInvitation invitation, List<GomokuRoomParticipant> members, Instant now) {
        var room = invitation.getRoom();
        return room.getStatus() == RoomStatus.WAITING && members.size() == 1
                && room.getHost().getId().equals(invitation.getInviter().getId())
                && room.getRevision() == invitation.getRoomRevision()
                && members.stream().anyMatch(p -> p.getUser().getId().equals(invitation.getInviter().getId())
                    && p.getMembershipId().equals(invitation.getHostMembershipId())
                    && !p.getLastSeenAt().isBefore(now.minusSeconds(90))
                    && (p.getExitDeadline() == null || p.getExitDeadline().isAfter(now)))
                && friendship.friends(invitation.getInviter().getId(), invitation.getTarget().getId());
    }

    private void finish(GomokuRoomInvitation invitation, State state, Instant now, String membershipId) {
        invitation.resolve(state, now, membershipId);
        friendship.changed(List.of(invitation.getInviter().getId(), invitation.getTarget().getId()));
    }

    private void lockUsers(Long first, Long second) {
        Stream.of(first, second).distinct().sorted().forEach(id -> users.findForUpdateById(id)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getDeletedAt() == null)
                .orElseThrow(() -> failure(ErrorCode.USER_NOT_FOUND)));
    }

    private GomokuRoom lockedRoom(Long roomId) {
        return rooms.findForUpdateById(roomId).orElseThrow(() -> failure(ErrorCode.ROOM_NOT_FOUND));
    }

    private static Decision decision(State state, Long roomId) { return Decision.builder().state(state).roomId(roomId).build(); }
    private static BusinessException failure(ErrorCode code) { return new BusinessException(code); }
    private static String uuid(String value) {
        try { if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); return value; }
        catch (RuntimeException error) { throw failure(ErrorCode.INVALID_REQUEST_BODY); }
    }
}
