package com.project.playground.domain.memory;

import com.project.playground.domain.social.*;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.*;
import com.project.playground.global.security.session.Session;
import lombok.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** 본인의 15초 친구 초대만 수락한다. 회원→방 순서로 잠가 입장과 친구 관계 경합을 직렬화한다. */
@Service @RequiredArgsConstructor @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
public class MemoryInvitationService {
    private final JdbcClient jdbc;
    private final MemoryService games;
    private final MemoryRoomRepository rooms;
    private final UserRepository users;
    private final SocialFriendshipPolicy friends;
    private final StringRedisTemplate redis;
    private final MemoryRateLimit limits;
    private final Clock clock;
    @Getter @Builder private static class Invite {
        private final String id;
        private final String requestId;
        private final Long roomId;
        private final Long inviter;
        private final Long target;
        private final Instant expires;
        private final String state;
    }
    private Invite read(ResultSet r,int n)throws SQLException{return Invite.builder().id(r.getString("id")).requestId(r.getString("request_id"))
        .roomId(r.getLong("room_id")).inviter(r.getLong("inviter_id")).target(r.getLong("target_id"))
        .expires(r.getTimestamp("expires_at").toInstant()).state(r.getString("state")).build();}
    public MemoryDtos.Invitation send(Session session,MemoryDtos.Command c){
        limits.acquire(session.getId(),"invite");MemoryService.uuid(c.getRequestId());MemoryService.uuid(c.getTabId());
        Long target=users.findActiveIdByPublicId(MemoryService.uuid(c.getTargetId())).orElseThrow(()->failure(ErrorCode.SOCIAL_ACCESS_DENIED));
        lock(session.getId(),target);var room=games.locked(c.getRoomId());
        if(!room.getHostId().equals(session.getId())||!Objects.equals(room.getHostTab(),c.getTabId())
            ||!Objects.equals(room.getHostLogin(),session.getSessionId().toString())||!available(room))throw failure(ErrorCode.MEMORY_INVITATION_EXPIRED);
        if(!friends.friends(session.getId(),target)||!Boolean.TRUE.equals(redis.hasKey("social:online:"+c.getTargetId()))
            ||!rooms.current(target).isEmpty())throw failure(ErrorCode.SOCIAL_ACCESS_DENIED);
        Instant now=clock.instant();
        var same=jdbc.sql("SELECT * FROM tbl_memory_invitation WHERE inviter_id=? AND request_id=?").params(session.getId(),c.getRequestId()).query(this::read).optional();
        if(same.isPresent()){
            var i=same.get();if(!i.roomId.equals(room.getId())||!i.target.equals(target)||!pending(i,now))throw failure(ErrorCode.MEMORY_INVITATION_EXPIRED);
            return response(i,room);
        }
        var pending=jdbc.sql("SELECT * FROM tbl_memory_invitation WHERE state='PENDING' AND expires_at>? AND (room_id=? OR target_id=?)")
            .params(Timestamp.from(now),room.getId(),target).query(this::read).list();
        if(!pending.isEmpty()){
            var first=pending.getFirst();if(first.roomId.equals(room.getId())&&first.target.equals(target))return response(first,room);
            throw failure(ErrorCode.MEMORY_INVITATION_BUSY);
        }
        var invite=Invite.builder().id(UUID.randomUUID().toString()).requestId(c.getRequestId()).roomId(room.getId())
            .inviter(session.getId()).target(target).expires(now.plusSeconds(15)).state("PENDING").build();
        jdbc.sql("INSERT INTO tbl_memory_invitation(id,request_id,room_id,inviter_id,target_id,expires_at,state) VALUES(?,?,?,?,?,?,'PENDING')")
            .params(invite.id,invite.requestId,invite.roomId,invite.inviter,invite.target,Timestamp.from(invite.expires)).update();
        friends.changed(List.of(session.getId(),target));return response(invite,room);
    }
    public MemoryDtos.Decision respond(Session session,MemoryDtos.Command c,String action){
        limits.acquire(session.getId(),"invitation-reply");
        var invite=jdbc.sql("SELECT * FROM tbl_memory_invitation WHERE id=?").param(MemoryService.uuid(c.getInvitationId())).query(this::read).optional()
            .orElseThrow(()->failure(ErrorCode.MEMORY_INVITATION_EXPIRED));
        if(!(action.equals("cancel")?invite.inviter:invite.target).equals(session.getId()))throw failure(ErrorCode.SOCIAL_ACCESS_DENIED);
        lock(invite.inviter,invite.target);var room=games.locked(invite.roomId);
        invite=jdbc.sql("SELECT * FROM tbl_memory_invitation WHERE id=? FOR UPDATE").param(invite.id).query(this::read).single();
        if(!invite.state.equals("PENDING")){
            boolean accepted=invite.state.equals("ACCEPTED")&&room.seat(session.getId())>0&&!room.isClosed();
            return MemoryDtos.Decision.builder().state(accepted?"ACCEPTED":invite.state.equals("ACCEPTED")?"CANCELLED":invite.state).roomId(accepted?room.getId():null).build();
        }
        String state;
        if(!pending(invite,clock.instant())||!available(room)||!friends.friends(invite.inviter,invite.target))state="EXPIRED";
        else if(action.equals("accept")){
            MemoryService.uuid(c.getTabId());
            games.joinInvited(room,session.getId(),session.getSessionId().toString(),c.getTabId());state="ACCEPTED";
        }else state=action.equals("cancel")?"CANCELLED":"REJECTED";
        jdbc.sql("UPDATE tbl_memory_invitation SET state=? WHERE id=?").params(state,invite.id).update();
        friends.changed(List.of(invite.inviter,invite.target));
        return MemoryDtos.Decision.builder().state(state).roomId(state.equals("ACCEPTED")?room.getId():null).build();
    }
    @Transactional(readOnly=true) public List<MemoryDtos.Invitation> visible(Long user){
        Instant now=clock.instant();
        return jdbc.sql("""
            SELECT i.* FROM tbl_memory_invitation i JOIN tbl_memory_room r ON r.id=i.room_id
            WHERE i.state='PENDING' AND i.expires_at>? AND (i.inviter_id=? OR i.target_id=?)
            AND r.closed=FALSE AND r.guest_id IS NULL AND r.phase='WAITING' ORDER BY i.expires_at LIMIT 20
            """).params(Timestamp.from(now),user,user).query(this::read).list().stream()
            .filter(i->friends.friends(i.inviter,i.target)).map(i->response(i,rooms.findById(i.roomId).orElseThrow())).toList();
    }
    private boolean pending(Invite i,Instant now){return i.state.equals("PENDING")&&now.isBefore(i.expires);}
    private boolean available(MemoryRoom r){return !r.isSolo()&&!r.isClosed()&&r.getGuestId()==null&&r.getBoard().getPhase().equals("WAITING");}
    private void lock(Long a,Long b){List.of(a,b).stream().distinct().sorted().forEach(games::lockUser);}
    private MemoryDtos.Invitation response(Invite i,MemoryRoom r){return MemoryDtos.Invitation.builder().id(i.id).roomId(r.getId()).roomTitle(r.getTitle())
        .inviter(person(users.findById(i.inviter).orElseThrow())).target(person(users.findById(i.target).orElseThrow())).expiresAt(i.expires).build();}
    private SocialDtos.Person person(User u){return SocialDtos.Person.builder().publicId(u.getPublicId()).nickname(u.getNickname()).avatarId(u.getProfileAvatar().name()).relation("FRIEND").build();}
    private static BusinessException failure(ErrorCode code){return new BusinessException(code);}
}
