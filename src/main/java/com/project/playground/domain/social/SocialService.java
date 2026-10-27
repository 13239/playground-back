package com.project.playground.domain.social;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import java.time.Instant;
import java.util.*;
import static com.project.playground.domain.social.SocialDtos.*;
import static com.project.playground.domain.social.SocialRows.Account;
import static com.project.playground.domain.social.SocialRows.Friend;

/** 본인의 인증 주체와 원본 관계만으로 명령을 수행한다. 클라이언트가 역할/발신자/입장 시각을 정할 수 없다. */
@Service @RequiredArgsConstructor
public class SocialService {
    private final SocialRepository repository;
    private final SocialProjector projector;
    private final StringRedisTemplate redis;
    private static final long FOREVER=Long.MAX_VALUE;
    private static BusinessException denied(){return new BusinessException(ErrorCode.SOCIAL_ACCESS_DENIED);}
    private static BusinessException conflict(){return new BusinessException(ErrorCode.SOCIAL_CONFLICT);}
    private static BusinessException missing(){return new BusinessException(ErrorCode.SOCIAL_NOT_FOUND);}
    private static BusinessException limit(){return new BusinessException(ErrorCode.SOCIAL_LIMIT_EXCEEDED);}
    private static String uuid(String value) {
        try { if(!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); return value; }
        catch(RuntimeException exception){throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);}
    }
    private Account require(long id){var user=repository.user(id);if(user==null)throw missing();return user;}
    private Account target(String id){var user=repository.user(uuid(id));if(user==null)throw missing();return user;}
    private int page(Integer value){if(value==null)return 1;if(value<1||value>10000)throw conflict();return value;}
    private void lock(long a,long b){repository.lockUsers(List.of(a,b));require(a);require(b);}
    private void friends(long a,long b) {
        var f=repository.relation(a,b);
        if(a==b||f==null||!f.getState().equals("ACCEPTED")||repository.blocked(a,b))throw denied();
    }
    private List<String> invalidate(List<String> ids) {
        var sorted=ids.stream().distinct().sorted().toList();
        sorted.forEach(id->repository.room(id,true));projector.invalidate(sorted);return sorted;
    }
    private void changed(Collection<Long> users,Collection<String> rooms) {
        users.stream().distinct().forEach(repository::outboxUser);
        rooms.stream().distinct().forEach(repository::outboxRoom);
        projector.afterCommit();
    }
    @Transactional(readOnly=true)
    public Snapshot snapshot(long me,Command c) {
        Account self=require(me);
        // Redis 재시작으로 유실된 권한은 원본에서만 복구한다. 복구 중 오류는 비공개 방 허용으로 바꾸지 않는다.
        projector.warm(me);
        String search=Objects.toString(c.getSearch(),"").trim();
        if(search.length()>50)throw conflict();
        var friendPage=repository.friends(me,page(c.getFriendPage()),search);
        var onlineKeys=friendPage.getItems().stream().map(p->"social:online:"+p.getPublicId()).toList();
        var online=onlineKeys.isEmpty()?List.<String>of():redis.opsForValue().multiGet(onlineKeys);
        var people=new ArrayList<Person>();
        for(int i=0;i<friendPage.getItems().size();i++) people.add(friendPage.getItems().get(i).toBuilder().online(online!=null&&online.get(i)!=null).build());
        int rp=page(c.getRoomPage());
        return Snapshot.builder().self(SocialRepository.person(self,"SELF",null))
            .friends(Page.of(people,friendPage.getTotal(),friendPage.getPage()))
            .received(repository.requests(me,false,page(c.getRequestPage()))).sent(repository.requests(me,true,page(c.getSentPage())))
            .rooms(Page.of(repository.roomIds(me,rp).stream().map(id->roomView(me,id)).toList(),repository.roomCount(me),rp))
            .notifications(repository.notifications(me,page(c.getNotificationPage()))).unreadNotifications(repository.unread(me))
            .notificationBoundary(repository.boundary(me)).invitations(repository.invitations(me,Instant.now())).build();
    }
    @Transactional(readOnly=true)
    public Person profile(long me,String id) {
        var person=target(id);require(me);
        Friend f=repository.relation(me,person.getId());
        boolean blocked=repository.blocks(me,person.getId());
        String relation=me==person.getId()?"SELF":blocked?"BLOCKED":repository.blocked(me,person.getId())?"UNAVAILABLE"
            :f==null?"NONE":f.getState().equals("ACCEPTED")?"FRIEND":f.getState().equals("PENDING")?(f.getRequester()==me?"SENT":"RECEIVED"):"NONE";
        return SocialRepository.person(person,relation,f!=null&&f.getState().equals("PENDING")?f.getId():null).toBuilder().blocked(blocked).build();
    }
    /** 오래된 알림도 받은 요청의 실제 번호 페이지로 이동한다. 처리된 요청은 다시 열지 않는다. */
    @Transactional(readOnly=true)
    public RequestLocation requestLocation(long me,String id) {
        require(me);var f=repository.request(uuid(id));
        if(f==null||!f.getState().equals("PENDING")||f.getRequester()==me||(f.getFirst()!=me&&f.getSecond()!=me))return RequestLocation.builder().active(false).page(1).build();
        long before=repository.count("SELECT COUNT(*) FROM tbl_social_friend WHERE state='PENDING' AND (first_user_id=? OR second_user_id=?) AND requester_id<>? AND (requested_at>? OR (requested_at=? AND request_id<?))",me,me,me,f.getRequestedAt(),f.getRequestedAt(),id);
        return RequestLocation.builder().active(true).page((int)(before/12)+1).build();
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Object command(long me,String action,Command c) {
        require(me);
        // 목록·연결 복구와 별개인 회원별 변경 예산으로 스팸/과도한 그룹 생성 요청을 제한한다.
        // 증가·만료를 한 번에 적용해 중단된 요청이 영구 제한 키를 남기지 않는다.
        var budget=org.springframework.data.redis.core.script.RedisScript.of(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",Long.class);
        Long used=redis.execute(budget,List.of("social:commands:"+me));
        if(used==null||used>60)throw limit();
        return switch(action) {
            case "friend-request" -> request(me,c.getTargetId());
            case "friend-accept","friend-reject","friend-cancel" -> resolve(me,action,c.getRequestId());
            case "friend-remove","block","unblock" -> remove(me,action,c.getTargetId());
            case "direct" -> direct(me,c.getTargetId());
            case "group-create" -> createGroup(me,c);
            case "room" -> roomView(me,uuid(c.getRoomId()));
            case "group-invite" -> invite(me,c);
            case "invitation-accept","invitation-reject","invitation-cancel" -> resolveInvite(me,action,c.getInvitationId());
            case "room-leave","room-kick","room-rename","room-mute" -> manage(me,action,c);
            case "notification-read" -> {repository.markRead(me,uuid(c.getNotificationId()),Instant.now());changed(List.of(me),List.of());yield Map.of("ok",true);}
            case "notifications-read" -> {if(c.getBeforeId()!=null)repository.markAll(me,uuid(c.getBeforeId()),Instant.now());changed(List.of(me),List.of());yield Map.of("ok",true);}
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        };
    }
    private Person request(long me,String id) {
        Account other=target(id);lock(me,other.getId());
        if(me==other.getId()||repository.blocked(me,other.getId()))throw denied();
        Friend f=repository.relation(me,other.getId());
        if(f!=null&&(f.getState().equals("PENDING")||f.getState().equals("ACCEPTED")))return profile(me,id);
        Instant now=Instant.now();
        if(f!=null&&f.getState().equals("REJECTED")&&f.getResolvedAt().isAfter(now.minusSeconds(86400)))throw limit();
        if(repository.dailyRequests(me,now.minusSeconds(86400))>=50||repository.friendCount(me)>=200
            ||repository.count("SELECT COUNT(*) FROM tbl_social_friend WHERE state='PENDING' AND (first_user_id=? OR second_user_id=?) AND requester_id<>?",other.getId(),other.getId(),other.getId())>=200)throw limit();
        String request=UUID.randomUUID().toString();
        repository.request(me,other.getId(),request,now);repository.notify(other.getId(),me,"FRIEND_REQUEST",request,now);
        changed(List.of(me,other.getId()),List.of());return profile(me,id);
    }
    private Object resolve(long me,String action,String id) {
        Friend first=repository.request(uuid(id));if(first==null)throw missing();
        lock(first.getFirst(),first.getSecond());Friend f=repository.request(id);
        if(f==null||(f.getFirst()!=me&&f.getSecond()!=me))throw denied();
        if(!f.getState().equals("PENDING"))return Map.of("ok",true);
        long other=f.getFirst()==me?f.getSecond():f.getFirst();
        if(action.equals("friend-cancel")?f.getRequester()!=me:f.getRequester()==me)throw denied();
        if(repository.blocked(me,other))throw denied();
        Instant now=Instant.now();
        String state=action.equals("friend-accept")?"ACCEPTED":action.equals("friend-reject")?"REJECTED":"CANCELLED";
        if(state.equals("ACCEPTED")&&(repository.friendCount(me)>=200||repository.friendCount(other)>=200))throw limit();
        var direct=repository.direct(me,other);
        List<String> rooms=direct==null?List.of():invalidate(List.of(direct.getId()));
        repository.resolve(id,state,now);repository.resolvedNotifications(id,now);
        if(state.equals("ACCEPTED")) {
            if(direct!=null)repository.update("UPDATE tbl_social_room SET read_until_micros=? WHERE id=?",FOREVER,direct.getId());
            repository.notify(other,me,"FRIEND_ACCEPTED",require(me).getPublicId(),now);
        }
        changed(List.of(me,other),rooms);return Map.of("ok",true);
    }
    private Object remove(long me,String action,String target) {
        Account other=target(target);lock(me,other.getId());if(me==other.getId())throw denied();
        List<String> rooms=invalidate(repository.sharedRooms(me,other.getId()));
        Instant now=Instant.now();
        if(action.equals("unblock"))repository.update("DELETE FROM tbl_social_block WHERE user_id=? AND target_id=?",me,other.getId());
        else {
            if(action.equals("block"))repository.update("INSERT IGNORE INTO tbl_social_block(user_id,target_id,created_at) VALUES(?,?,?)",me,other.getId(),now);
            Friend f=repository.relation(me,other.getId());
            if(f!=null){repository.resolve(f.getId(),"REMOVED",now);repository.resolvedNotifications(f.getId(),now);}
            var direct=repository.direct(me,other.getId());
            if(direct!=null)repository.update("UPDATE tbl_social_room SET read_until_micros=LEAST(read_until_micros,?) WHERE id=?",projector.nowMicros(),direct.getId());
            repository.update("UPDATE tbl_social_invitation SET state='CANCELLED' WHERE state='PENDING' AND ((inviter_id=? AND target_id=?) OR (inviter_id=? AND target_id=?))",me,other.getId(),other.getId(),me);
        }
        changed(List.of(me,other.getId()),rooms);return Map.of("ok",true);
    }
    private SocialDtos.Room direct(long me,String id) {
        Account other=target(id);lock(me,other.getId());friends(me,other.getId());
        var existing=repository.direct(me,other.getId());
        if(existing!=null)return roomView(me,existing.getId());
        if(repository.roomCount(me)>=100||repository.roomCount(other.getId())>=100)throw limit();
        String room=UUID.randomUUID().toString();long since=projector.nowMicros();
        repository.createRoom(room,"DIRECT","",me,SocialRepository.pair(me,other.getId()),Instant.now());
        repository.join(room,me,since);repository.join(room,other.getId(),since);
        changed(List.of(me,other.getId()),List.of(room));return roomView(me,room);
    }
    private String name(String value) {if(value==null||value.strip().isEmpty()||value.strip().codePointCount(0,value.strip().length())>50)throw conflict();return value.strip();}
    private SocialDtos.Room createGroup(long me,Command c) {
        if(c.getUserIds()==null||c.getUserIds().size()<2||c.getUserIds().size()>9)throw conflict();
        var users=c.getUserIds().stream().map(this::target).toList();
        if(users.stream().map(Account::getId).distinct().count()!=users.size())throw conflict();
        var locks=new ArrayList<Long>();locks.add(me);users.forEach(p->locks.add(p.getId()));repository.lockUsers(locks);
        if(repository.roomCount(me)>=100)throw limit();
        users.forEach(p->friends(me,p.getId()));
        String room=UUID.randomUUID().toString();
        repository.createRoom(room,"GROUP",name(c.getName()),me,null,Instant.now());repository.join(room,me,projector.nowMicros());
        for(var user:users)addInvite(me,room,user);
        changed(locks,List.of(room));return roomView(me,room);
    }
    private SocialRows.Member member(long me,String room) {
        return repository.members(room).stream().filter(m->m.isActive()&&m.getUserId()==me).findFirst().orElseThrow(SocialService::denied);
    }
    private SocialRows.Room group(String room) {
        var r=repository.room(uuid(room),true);if(r==null||r.isClosed()||!r.getKind().equals("GROUP"))throw denied();return r;
    }
    /** 회원→방 순서의 잠금을 모든 초대/관리 명령이 공유한다. 기다리는 동안 새 참가자가 생기면 재조회한다. */
    private void lockRoomUsers(String room,Collection<Long> extra) {
        uuid(room);
        var users=new HashSet<Long>(extra);
        repository.members(room).stream().filter(SocialRows.Member::isActive).forEach(m->users.add(m.getUserId()));
        repository.lockUsers(users);repository.room(room,true);
        if(repository.members(room).stream().filter(SocialRows.Member::isActive).anyMatch(m->!users.contains(m.getUserId())))throw conflict();
    }
    private void addInvite(long me,String room,Account target) {
        friends(me,target.getId());
        var members=repository.members(room).stream().filter(SocialRows.Member::isActive).toList();
        if(members.stream().anyMatch(m->m.getUserId()==target.getId()||repository.blocked(m.getUserId(),target.getId())))throw denied();
        var existing=repository.list("SELECT id FROM tbl_social_invitation WHERE room_id=? AND target_id=? AND state='PENDING' AND expires_at>?",(r,n)->r.getString(1),room,target.getId(),Instant.now());
        if(!existing.isEmpty())return;
        // 수신함에 표시 가능한 대기 초대 수를 제한해 보이지 않는 초대가 쌓이지 않게 한다.
        if(repository.count("SELECT COUNT(*) FROM tbl_social_invitation WHERE target_id=? AND state='PENDING' AND expires_at>?",target.getId(),Instant.now())>=100)throw limit();
        if(members.size()+repository.reserved(room,Instant.now())>=10)throw limit();
        String id=UUID.randomUUID().toString();repository.invite(id,room,me,target.getId(),Instant.now());
        repository.notify(target.getId(),me,"GROUP_INVITATION",id,Instant.now());repository.outboxUser(target.getId());
    }
    private Object invite(long me,Command c) {
        if(c.getUserIds()==null||c.getUserIds().isEmpty()||c.getUserIds().size()>9)throw conflict();
        var targets=c.getUserIds().stream().map(this::target).toList();
        var users=new ArrayList<Long>();users.add(me);targets.forEach(p->users.add(p.getId()));
        lockRoomUsers(c.getRoomId(),users);group(c.getRoomId());member(me,c.getRoomId());
        for(var person:targets)addInvite(me,c.getRoomId(),person);
        changed(List.of(me),List.of(c.getRoomId()));return Map.of("ok",true);
    }
    private Object resolveInvite(long me,String action,String id) {
        var invite=repository.invitation(uuid(id));if(invite==null)throw missing();
        lockRoomUsers(invite.getRoomId(),List.of(me,invite.getInviter(),invite.getTarget()));
        var room=group(invite.getRoomId());invite=repository.invitation(id);
        if(action.equals("invitation-cancel")) {
            if(invite.getInviter()!=me&&room.getOwner()!=me)throw denied();
        } else if(invite.getTarget()!=me)throw denied();
        if(!invite.getState().equals("PENDING"))return Map.of("ok",true);
        if(action.equals("invitation-accept")) {
            if(!invite.getExpiresAt().isAfter(Instant.now()))throw conflict();
            member(invite.getInviter(),room.getId());friends(me,invite.getInviter());
            var members=repository.members(room.getId()).stream().filter(SocialRows.Member::isActive).toList();
            if(members.size()>=10||repository.roomCount(me)>=100)throw limit();
            for(var m:members)if(repository.blocked(me,m.getUserId()))throw denied();
            invalidate(List.of(room.getId()));repository.join(room.getId(),me,projector.nowMicros());
        }
        repository.update("UPDATE tbl_social_invitation SET state=? WHERE id=?",action.equals("invitation-accept")?"ACCEPTED":action.equals("invitation-cancel")?"CANCELLED":"REJECTED",id);
        repository.resolvedNotifications(id,Instant.now());changed(List.of(me,invite.getInviter(),invite.getTarget()),List.of(room.getId()));
        return action.equals("invitation-accept")?roomView(me,room.getId()):Map.of("ok",true);
    }
    private Object manage(long me,String action,Command c) {
        lockRoomUsers(c.getRoomId(),List.of(me));
        var room=repository.room(uuid(c.getRoomId()),true);if(room==null||room.isClosed())throw denied();
        var self=member(me,room.getId());
        if(action.equals("room-mute")) {
            repository.update("UPDATE tbl_social_member SET muted=? WHERE room_id=? AND user_id=?",Boolean.TRUE.equals(c.getMuted()),room.getId(),me);
            changed(List.of(me),List.of(room.getId()));return Map.of("ok",true);
        }
        if(!room.getKind().equals("GROUP"))throw denied();
        if(action.equals("room-rename")) {
            if(room.getOwner()!=me)throw denied();
            repository.update("UPDATE tbl_social_room SET name=? WHERE id=?",name(c.getName()),room.getId());
        } else {
            long leaving=action.equals("room-leave")?me:target(c.getTargetId()).getId();
            if(leaving!=me&&room.getOwner()!=me)throw denied();
            if(action.equals("room-kick")&&leaving==me)throw denied();
            member(leaving,room.getId());invalidate(List.of(room.getId()));
            repository.update("UPDATE tbl_social_member SET active=FALSE WHERE room_id=? AND user_id=?",room.getId(),leaving);
            repository.update("UPDATE tbl_social_invitation SET state='CANCELLED' WHERE room_id=? AND inviter_id=? AND state='PENDING'",room.getId(),leaving);
            var remaining=repository.members(room.getId()).stream().filter(SocialRows.Member::isActive).toList();
            if(remaining.isEmpty()){
                repository.update("UPDATE tbl_social_room SET closed_at=? WHERE id=?",Instant.now(),room.getId());
                repository.update("UPDATE tbl_social_invitation SET state='CANCELLED' WHERE room_id=? AND state='PENDING'",room.getId());
            }else if(room.getOwner()==leaving)repository.update("UPDATE tbl_social_room SET owner_id=? WHERE id=?",remaining.getFirst().getUserId(),room.getId());
        }
        var users=repository.members(room.getId()).stream().map(SocialRows.Member::getUserId).toList();
        changed(users,List.of(room.getId()));return Map.of("ok",true);
    }
    private SocialDtos.Room roomView(long me,String id) {
        var room=repository.room(id,false);if(room==null||room.isClosed())throw missing();
        var self=member(me,id);var members=repository.members(id).stream().filter(SocialRows.Member::isActive).toList();
        boolean writable=true;String title=room.getName();
        if(room.getKind().equals("DIRECT")) {
            var peer=members.stream().filter(m->m.getUserId()!=me).findFirst().orElseThrow(SocialService::missing);
            title=peer.getPerson().getNickname();
            var relation=repository.relation(me,peer.getUserId());
            writable=relation!=null&&relation.getState().equals("ACCEPTED")&&!repository.blocked(me,peer.getUserId());
        }
        return SocialDtos.Room.builder().id(id).kind(room.getKind()).name(title).owner(room.getOwner()==me).writable(writable)
            .muted(self.isMuted()).membershipId(self.getMembershipId())
            .invitations(room.getKind().equals("GROUP")?repository.pendingInvitations(id,me,room.getOwner()==me):List.of())
            .members(members.stream().map(m->Member.builder().person(SocialRepository.person(m.getPerson(),"NONE",null)).owner(m.getUserId()==room.getOwner()).self(m.getUserId()==me).build()).toList()).build();
    }
}

