package com.project.playground.domain.gameinvite;

import com.project.playground.domain.social.*;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.*;
import com.project.playground.global.security.session.Session;
import lombok.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** 신규 게임의 15초 초대·중복 요청·권한·수락 경합을 공통으로 처리한다. 실제 방 조건은 어댑터가 검사한다. */
@Service @RequiredArgsConstructor
@Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
public class GameInvitationService {
    private final JdbcClient jdbc;
    private final UserRepository users;
    private final SocialFriendshipPolicy friendship;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final List<GameInvitationAdapter> adapters;
    private static final RedisScript<Long> RATE=RedisScript.of("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",Long.class);
    @Getter @Builder private static class Invite {
        private final String id, game, roomId, roomVersion, requestId, inviterSession, state;
        private final Long inviter, target;
        private final Instant expires;
    }
    private Invite read(ResultSet r,int n)throws SQLException {
        return Invite.builder().id(r.getString("id")).game(r.getString("game_code")).roomId(r.getString("room_id"))
            .roomVersion(r.getString("room_version")).requestId(r.getString("request_id")).inviterSession(r.getString("inviter_session"))
            .inviter(r.getLong("inviter_id")).target(r.getLong("target_id")).expires(r.getTimestamp("expires_at").toInstant()).state(r.getString("state")).build();
    }
    public GameInvitationDtos.Invitation send(Session session,GameInvitationDtos.Command c) {
        rate(session.getId());var adapter=adapter(c.getGame());uuid(c.getRequestId());uuid(c.getTargetId());
        Long target=users.findActiveIdByPublicId(c.getTargetId()).orElseThrow(()->failure(ErrorCode.SOCIAL_ACCESS_DENIED));
        lock(session.getId(),target);
        var room=adapter.requireHost(session,c.getRoomId());
        if(!friendship.friends(session.getId(),target)||!Boolean.TRUE.equals(redis.hasKey("social:online:"+c.getTargetId()))||!adapter.targetAvailable(target))throw failure(ErrorCode.GAME_INVITATION_TARGET_UNAVAILABLE);
        Instant now=clock.instant();
        var prior=jdbc.sql("SELECT * FROM tbl_game_invitation WHERE inviter_id=? AND request_id=?").params(session.getId(),c.getRequestId()).query(this::read).optional();
        if(prior.isPresent()){
            var i=prior.get();
            if(!i.game.equals(c.getGame())||!i.roomId.equals(room.getId())||!i.target.equals(target)||!i.roomVersion.equals(room.getVersion())||!i.inviterSession.equals(session.getSessionId().toString())||!pending(i,now))throw failure(ErrorCode.GAME_INVITATION_EXPIRED);
            return response(i,room);
        }
        // 같은 회원 잠금으로 발신 방과 수신자별 하나의 응답 대기만 허용한다.
        var active=jdbc.sql("SELECT * FROM tbl_game_invitation WHERE state='PENDING' AND expires_at>? AND ((game_code=? AND room_id=?) OR target_id=?)")
            .params(Timestamp.from(now),c.getGame(),room.getId(),target).query(this::read).list();
        for(var i:active){
            var current=adapter(i.game).available(i.roomId,i.inviter,i.inviterSession);
            if(current.isEmpty()||!current.get().getVersion().equals(i.roomVersion)){
                jdbc.sql("UPDATE tbl_game_invitation SET state='CANCELLED' WHERE id=? AND state='PENDING'").param(i.id).update();
            }else if(i.game.equals(c.getGame())&&i.roomId.equals(room.getId())&&i.target.equals(target))return response(i,room);
            else throw failure(ErrorCode.GAME_INVITATION_BUSY);
        }
        var i=Invite.builder().id(UUID.randomUUID().toString()).game(c.getGame()).roomId(room.getId()).roomVersion(room.getVersion())
            .requestId(c.getRequestId()).inviter(session.getId()).inviterSession(session.getSessionId().toString()).target(target).expires(now.plusSeconds(15)).state("PENDING").build();
        jdbc.sql("INSERT INTO tbl_game_invitation(id,game_code,room_id,room_version,request_id,inviter_id,inviter_session,target_id,expires_at,state) VALUES(?,?,?,?,?,?,?,?,?,'PENDING')")
            .params(i.id,i.game,i.roomId,i.roomVersion,i.requestId,i.inviter,i.inviterSession,i.target,Timestamp.from(i.expires)).update();
        friendship.changed(List.of(i.inviter,i.target));return response(i,room);
    }
    public GameInvitationDtos.Decision respond(Session session,GameInvitationDtos.Command c,String action){
        rate(session.getId());uuid(c.getInvitationId());
        if(!List.of("accept","reject","cancel").contains(action))throw failure(ErrorCode.INVALID_REQUEST_BODY);
        var original=jdbc.sql("SELECT * FROM tbl_game_invitation WHERE id=? AND game_code=?").params(c.getInvitationId(),c.getGame()).query(this::read).optional().orElseThrow(()->failure(ErrorCode.GAME_INVITATION_EXPIRED));
        if(!(action.equals("cancel")?original.inviter:original.target).equals(session.getId()))throw failure(ErrorCode.SOCIAL_ACCESS_DENIED);
        lock(original.inviter,original.target);
        var i=jdbc.sql("SELECT * FROM tbl_game_invitation WHERE id=? FOR UPDATE").param(original.id).query(this::read).single();
        var room=adapter(i.game).available(i.roomId,i.inviter,i.inviterSession);
        // 15초는 최초 응답 기한이다. 이미 수락한 응답의 재조회가 기한 경과만으로 취소되지는 않는다.
        boolean valid=room.isPresent()&&room.get().getVersion().equals(i.roomVersion)&&friendship.friends(i.inviter,i.target)
            &&(!i.state.equals("PENDING")||clock.instant().isBefore(i.expires));
        if(!i.state.equals("PENDING"))return decision(i.state.equals("ACCEPTED")&&!valid?"CANCELLED":i.state,i.state.equals("ACCEPTED")&&valid?i.roomId:null);
        String state=!valid?"EXPIRED":action.equals("accept")?"ACCEPTED":action.equals("cancel")?"CANCELLED":"REJECTED";
        if(state.equals("ACCEPTED")&&!adapter(i.game).targetAvailable(i.target))throw failure(ErrorCode.GAME_INVITATION_TARGET_UNAVAILABLE);
        jdbc.sql("UPDATE tbl_game_invitation SET state=? WHERE id=?").params(state,i.id).update();
        friendship.changed(List.of(i.inviter,i.target));return decision(state,state.equals("ACCEPTED")?i.roomId:null);
    }
    @Transactional(readOnly=true)
    public List<GameInvitationDtos.Invitation> visible(Long user){
        return jdbc.sql("SELECT * FROM tbl_game_invitation WHERE state='PENDING' AND expires_at>? AND (inviter_id=? OR target_id=?) ORDER BY expires_at LIMIT 20")
            .params(Timestamp.from(clock.instant()),user,user).query(this::read).list().stream().filter(i->friendship.friends(i.inviter,i.target))
            .flatMap(i->adapter(i.game).available(i.roomId,i.inviter,i.inviterSession).filter(r->r.getVersion().equals(i.roomVersion)).map(r->response(i,r)).stream()).toList();
    }
    private GameInvitationAdapter adapter(String game){return adapters.stream().filter(a->a.game().equals(game)).findFirst().orElseThrow(()->failure(ErrorCode.INVALID_REQUEST_BODY));}
    private void lock(Long a,Long b){List.of(a,b).stream().distinct().sorted().forEach(id->users.findForUpdateById(id).filter(u->u.getStatus()==UserStatus.ACTIVE&&u.getDeletedAt()==null).orElseThrow(()->failure(ErrorCode.USER_NOT_FOUND)));}
    private void rate(Long user){Long count=redis.execute(RATE,List.of("game-invitation:limit:"+user));if(count==null||count>40)throw failure(ErrorCode.GAME_INVITATION_RATE_LIMIT);}
    private boolean pending(Invite i,Instant now){return i.state.equals("PENDING")&&now.isBefore(i.expires);}
    private GameInvitationDtos.Invitation response(Invite i,GameInvitationDtos.Room room){return GameInvitationDtos.Invitation.builder().id(i.id).game(i.game).roomId(i.roomId).roomTitle(room.getTitle()).inviter(person(users.findById(i.inviter).orElseThrow())).target(person(users.findById(i.target).orElseThrow())).expiresAt(i.expires).build();}
    private SocialDtos.Person person(User u){return SocialDtos.Person.builder().publicId(u.getPublicId()).nickname(u.getNickname()).avatarId(u.getProfileAvatar().name()).relation("FRIEND").build();}
    private GameInvitationDtos.Decision decision(String state,String room){return GameInvitationDtos.Decision.builder().state(state).roomId(room).build();}
    private static BusinessException failure(ErrorCode code){return new BusinessException(code);}
    private static void uuid(String value){try{if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException();}catch(RuntimeException e){throw failure(ErrorCode.INVALID_REQUEST_BODY);}}
}
