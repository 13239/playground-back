package com.project.playground.domain.social;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.project.playground.domain.social.SocialRows.*;
import com.project.playground.domain.social.SocialDtos.Person;
import com.project.playground.domain.social.SocialDtos.Request;
import com.project.playground.domain.social.SocialDtos.Invitation;
import com.project.playground.domain.social.SocialDtos.Notification;
import com.project.playground.domain.social.SocialDtos.Page;

/** 관계의 고유 제약·정렬된 행 잠금과 목록 조회를 한 저장소에 모은다.
 * 회원 엔티티의 기존 JPA 트랜잭션을 공유하며 관계 조인/잠금은 바인딩 SQL로 명시한다. */
@Repository @RequiredArgsConstructor
class SocialRepository {
    private final JdbcClient jdbc;
    private static final String USER_COLUMNS = "u.id,u.public_id,u.nickname,u.profile_avatar";
    private static final RowMapper<Account> ACCOUNT = (r,n) -> account(r);
    static Account account(ResultSet r) throws SQLException {
        return Account.builder().id(r.getLong("id")).publicId(r.getString("public_id"))
            .nickname(r.getString("nickname")).avatarId(r.getString("profile_avatar")).build();
    }
    <T> List<T> list(String sql, RowMapper<T> mapper, Object... args) { return jdbc.sql(sql).params(parameters(args)).query(mapper).list(); }
    int update(String sql, Object... args) { return jdbc.sql(sql).params(parameters(args)).update(); }
    long count(String sql, Object... args) { return jdbc.sql(sql).params(parameters(args)).query(Long.class).single(); }
    private List<?> parameters(Object[] args) {
        return Arrays.stream(args).map(v -> v instanceof Instant instant ? Timestamp.from(instant) : v).toList();
    }
    Account user(long id) { return first(list("SELECT "+USER_COLUMNS+" FROM tbl_user u WHERE u.id=? AND u.deleted_at IS NULL AND u.status='ACTIVE'", ACCOUNT,id)); }
    Account user(String id) { return first(list("SELECT "+USER_COLUMNS+" FROM tbl_user u WHERE u.public_id=? AND u.deleted_at IS NULL AND u.status='ACTIVE'",ACCOUNT,id)); }
    void lockUsers(Collection<Long> ids) {
        // 친구 요청·차단의 경합은 항상 같은 회원 순서로 잠가 직렬화한다.
        ids.stream().distinct().sorted().forEach(id -> list("SELECT id FROM tbl_user WHERE id=? FOR UPDATE",(r,n)->r.getLong(1),id));
    }
    Friend relation(long a,long b) { return first(list("SELECT * FROM tbl_social_friend WHERE first_user_id=? AND second_user_id=?", FRIEND,Math.min(a,b),Math.max(a,b))); }
    Friend request(String id) { return first(list("SELECT * FROM tbl_social_friend WHERE request_id=?",FRIEND,id)); }
    private static final RowMapper<Friend> FRIEND=(r,n)->Friend.builder().first(r.getLong("first_user_id")).second(r.getLong("second_user_id"))
        .requester(r.getLong("requester_id")).id(r.getString("request_id")).state(r.getString("state"))
        .requestedAt(r.getTimestamp("requested_at").toInstant()).resolvedAt(r.getTimestamp("resolved_at")==null?null:r.getTimestamp("resolved_at").toInstant()).build();
    boolean blocked(long a,long b) { return count("SELECT COUNT(*) FROM tbl_social_block WHERE (user_id=? AND target_id=?) OR (user_id=? AND target_id=?)",a,b,b,a)>0; }
    boolean blocks(long a,long b) { return count("SELECT COUNT(*) FROM tbl_social_block WHERE user_id=? AND target_id=?",a,b)>0; }
    List<Long> blockedAuthors(long user) { return list("SELECT target_id FROM tbl_social_block WHERE user_id=?",(r,n)->r.getLong(1),user); }
    void request(long a,long b,String id,Instant now) {
        update("INSERT INTO tbl_social_friend(first_user_id,second_user_id,request_id,requester_id,state,requested_at) VALUES(?,?,?,?,'PENDING',?) ON DUPLICATE KEY UPDATE request_id=?,requester_id=?,state='PENDING',requested_at=?,resolved_at=NULL",
            Math.min(a,b),Math.max(a,b),id,a,now,id,a,now);
    }
    void resolve(String id,String state,Instant now) { update("UPDATE tbl_social_friend SET state=?,resolved_at=? WHERE request_id=?",state,now,id); }
    long friendCount(long id) { return count("SELECT COUNT(*) FROM tbl_social_friend WHERE (first_user_id=? OR second_user_id=?) AND state='ACCEPTED'",id,id); }
    long dailyRequests(long id,Instant since) { return count("SELECT COUNT(*) FROM tbl_social_notification WHERE actor_id=? AND kind='FRIEND_REQUEST' AND created_at>=?",id,since); }
    Page<Person> friends(long id,int page,String search) {
        String from=" FROM tbl_social_friend f JOIN tbl_user u ON u.id=IF(f.first_user_id=?,f.second_user_id,f.first_user_id) WHERE (f.first_user_id=? OR f.second_user_id=?) AND f.state='ACCEPTED' AND u.status='ACTIVE' AND u.deleted_at IS NULL AND u.nickname LIKE ? ESCAPE '!'";
        String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        Object[] args={id,id,id,pattern};
        long total=count("SELECT COUNT(*)"+from,args);
        var values=list("SELECT "+USER_COLUMNS+from+" ORDER BY u.nickname,u.public_id LIMIT 12 OFFSET ?",(r,n)->person(account(r),"FRIEND",null),id,id,id,pattern,(page-1)*12);
        return Page.of(values,total,page);
    }
    Page<Request> requests(long id,boolean sent,int page) {
        String predicate=sent?"f.requester_id=?":"f.requester_id<>?";
        String from=" FROM tbl_social_friend f JOIN tbl_user u ON u.id=IF(f.first_user_id=?,f.second_user_id,f.first_user_id) WHERE (f.first_user_id=? OR f.second_user_id=?) AND "+predicate+" AND f.state='PENDING' AND u.status='ACTIVE' AND u.deleted_at IS NULL";
        long total=count("SELECT COUNT(*)"+from,id,id,id,id);
        var values=list("SELECT "+USER_COLUMNS+",f.request_id,f.requested_at"+from+" ORDER BY f.requested_at DESC,f.request_id LIMIT 12 OFFSET ?",
            (r,n)->Request.builder().id(r.getString("request_id")).person(person(account(r),sent?"SENT":"RECEIVED",r.getString("request_id"))).createdAt(r.getTimestamp("requested_at").toInstant()).build(),id,id,id,id,(page-1)*12);
        return Page.of(values,total,page);
    }
    static Person person(Account user,String relation,String request) { return Person.builder().publicId(user.getPublicId()).nickname(user.getNickname()).avatarId(user.getAvatarId()).relation(relation).requestId(request).build(); }
    private static final RowMapper<Room> ROOM=(r,n)->Room.builder().id(r.getString("id")).kind(r.getString("kind")).name(r.getString("name")).owner(r.getLong("owner_id")).until(r.getLong("read_until_micros")).closed(r.getTimestamp("closed_at")!=null).build();
    Room room(String id,boolean lock) { return first(list("SELECT * FROM tbl_social_room WHERE id=?"+(lock?" FOR UPDATE":""),ROOM,id)); }
    Room direct(long a,long b) { return first(list("SELECT * FROM tbl_social_room WHERE pair_key=?",ROOM,pair(a,b))); }
    static String pair(long a,long b) { return Math.min(a,b)+":"+Math.max(a,b); }
    void createRoom(String id,String kind,String name,long owner,String pair,Instant now) { update("INSERT INTO tbl_social_room(id,kind,name,owner_id,pair_key,created_at) VALUES(?,?,?,?,?,?)",id,kind,name,owner,pair,now); }
    List<Member> members(String room) { return list("SELECT "+USER_COLUMNS+",m.membership_id,m.joined_at_micros,m.active,m.muted FROM tbl_social_member m JOIN tbl_user u ON u.id=m.user_id WHERE m.room_id=? ORDER BY m.joined_at_micros,u.id",
        (r,n)->Member.builder().userId(r.getLong("id")).person(account(r)).membershipId(r.getString("membership_id")).since(r.getLong("joined_at_micros")).active(r.getBoolean("active")).muted(r.getBoolean("muted")).build(),room); }
    void join(String room,long user,long micros) {
        String membership=UUID.randomUUID().toString();
        update("INSERT INTO tbl_social_member(room_id,user_id,membership_id,joined_at_micros,active) VALUES(?,?,?,?,TRUE) ON DUPLICATE KEY UPDATE membership_id=?,joined_at_micros=?,active=TRUE",
            room,user,membership,micros,membership,micros);
    }
    List<String> roomsFor(long id) { return list("SELECT room_id FROM tbl_social_member WHERE user_id=? AND active=TRUE ORDER BY room_id",(r,n)->r.getString(1),id); }
    List<String> sharedRooms(long a,long b) { return list("SELECT a.room_id FROM tbl_social_member a JOIN tbl_social_member b ON a.room_id=b.room_id WHERE a.user_id=? AND b.user_id=? AND a.active=TRUE AND b.active=TRUE ORDER BY a.room_id",(r,n)->r.getString(1),a,b); }
    List<String> roomIds(long id,int page) { return list("SELECT r.id FROM tbl_social_room r JOIN tbl_social_member m ON m.room_id=r.id WHERE m.user_id=? AND m.active=TRUE AND r.closed_at IS NULL ORDER BY r.created_at DESC,r.id LIMIT 12 OFFSET ?",(r,n)->r.getString(1),id,(page-1)*12); }
    long roomCount(long id) { return count("SELECT COUNT(*) FROM tbl_social_member m JOIN tbl_social_room r ON r.id=m.room_id WHERE m.user_id=? AND m.active=TRUE AND r.closed_at IS NULL",id); }
    private static final RowMapper<Invite> INVITE=(r,n)->Invite.builder().id(r.getString("id")).roomId(r.getString("room_id")).inviter(r.getLong("inviter_id")).target(r.getLong("target_id")).state(r.getString("state")).expiresAt(r.getTimestamp("expires_at").toInstant()).build();
    Invite invitation(String id) { return first(list("SELECT * FROM tbl_social_invitation WHERE id=?",INVITE,id)); }
    List<Invitation> invitations(long user,Instant now) { return list("SELECT i.id AS invitation_id,i.room_id,i.expires_at,r.name,"+USER_COLUMNS+" FROM tbl_social_invitation i JOIN tbl_social_room r ON r.id=i.room_id JOIN tbl_user u ON u.id=i.inviter_id WHERE i.target_id=? AND i.state='PENDING' AND i.expires_at>? AND r.closed_at IS NULL ORDER BY i.created_at DESC LIMIT 100",
        (r,n)->Invitation.builder().id(r.getString(1)).roomId(r.getString(2)).expiresAt(r.getTimestamp(3).toInstant()).roomName(r.getString(4)).inviter(person(account(r),"FRIEND",null)).build(),user,now); }
    long reserved(String room,Instant now) { return count("SELECT COUNT(*) FROM tbl_social_invitation WHERE room_id=? AND state='PENDING' AND expires_at>?",room,now); }
    List<SocialDtos.PendingInvitation> pendingInvitations(String room,long viewer,boolean owner) {
        return list("SELECT i.id AS invitation_id,i.inviter_id,i.expires_at,"+USER_COLUMNS+" FROM tbl_social_invitation i JOIN tbl_user u ON u.id=i.target_id WHERE i.room_id=? AND i.state='PENDING' AND i.expires_at>? ORDER BY i.created_at LIMIT 9",
            (r,n)->SocialDtos.PendingInvitation.builder().id(r.getString("invitation_id")).person(person(account(r),"NONE",null)).expiresAt(r.getTimestamp("expires_at").toInstant()).cancellable(owner||r.getLong("inviter_id")==viewer).build(),room,Instant.now());
    }
    void invite(String id,String room,long from,long target,Instant now) { update("INSERT INTO tbl_social_invitation(id,room_id,inviter_id,target_id,state,created_at,expires_at) VALUES(?,?,?,?,'PENDING',?,?) ON DUPLICATE KEY UPDATE id=?,inviter_id=?,state='PENDING',created_at=?,expires_at=?",id,room,from,target,now,now.plusSeconds(604800),id,from,now,now.plusSeconds(604800)); }
    void notify(long user,long actor,String kind,String target,Instant now) { update("INSERT INTO tbl_social_notification(public_id,user_id,actor_id,kind,target_id,created_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),user,actor,kind,target,now); }
    Page<Notification> notifications(long user,int page) {
        long total=count("SELECT COUNT(*) FROM tbl_social_notification WHERE user_id=?",user);
        var values=list("SELECT n.public_id AS notification_id,n.kind,n.target_id,n.created_at,n.read_at,"+USER_COLUMNS+" FROM tbl_social_notification n JOIN tbl_user u ON u.id=n.actor_id WHERE n.user_id=? ORDER BY n.id DESC LIMIT 12 OFFSET ?",
            (r,n)->Notification.builder().id(r.getString("notification_id")).kind(r.getString("kind")).targetId(r.getString("target_id")).actor(person(account(r),"NONE",null)).createdAt(r.getTimestamp("created_at").toInstant()).read(r.getTimestamp("read_at")!=null).build(),user,(page-1)*12);
        return Page.of(values,total,page);
    }
    long unread(long id) { return count("SELECT COUNT(*) FROM tbl_social_notification WHERE user_id=? AND read_at IS NULL",id); }
    String boundary(long id) { return first(list("SELECT public_id FROM tbl_social_notification WHERE user_id=? ORDER BY id DESC LIMIT 1",(r,n)->r.getString(1),id)); }
    // 실제로 바뀐 행 수를 반환해 중복 읽음 요청이 실시간 갱신을 다시 발생시키지 않게 한다.
    int markRead(long user,String id,Instant now) { return update("UPDATE tbl_social_notification SET read_at=? WHERE user_id=? AND public_id=? AND read_at IS NULL",now,user,id); }
    int markAll(long user,String boundary,Instant now) {
        // 공개 UUID로 받은 경계를 본인의 내부 순서로 다시 조회하여 동시 도착 알림은 읽음 처리하지 않는다.
        var ids=list("SELECT id FROM tbl_social_notification WHERE user_id=? AND public_id=?",(r,n)->r.getLong(1),user,boundary);
        return ids.isEmpty()?0:update("UPDATE tbl_social_notification SET read_at=? WHERE user_id=? AND id<=? AND read_at IS NULL",now,user,ids.getFirst());
    }
    void resolvedNotifications(String target,Instant now) { update("UPDATE tbl_social_notification SET read_at=COALESCE(read_at,?) WHERE target_id=?",now,target); }
    void outboxRoom(String id) { update("INSERT INTO tbl_social_outbox(room_id) VALUES(?)",id); }
    void outboxUser(long id) { update("INSERT INTO tbl_social_outbox(user_id) VALUES(?)",id); }
    static <T> T first(List<T> values) { return values.isEmpty()?null:values.getFirst(); }
}

