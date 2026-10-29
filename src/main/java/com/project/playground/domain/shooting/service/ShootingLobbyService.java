package com.project.playground.domain.shooting.service;

import com.project.playground.domain.gameinvite.*;
import com.project.playground.domain.game.service.GameAvailabilityService;
import com.project.playground.domain.shooting.config.ShootingProperties;
import com.project.playground.domain.social.SocialFriendshipPolicy;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.*;
import com.project.playground.global.security.session.Session;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** 인증된 게임 서버의 짧은 대기실 임대만 목록에 공개한다. 전투·만석·퇴장·서버 종료 방은 노출하지 않는다. */
@Service @RequiredArgsConstructor
public class ShootingLobbyService implements GameInvitationAdapter {
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final UserRepository users;
    private final SocialFriendshipPolicy friends;
    private final ShootingProperties properties;
    private final GameAvailabilityService availability;
    private static final String INDEX="shooting:lobby:index";
    private static final RedisScript<Long> RATE=RedisScript.of("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",Long.class);
    private static final RedisScript<Long> PUBLISH=RedisScript.of("""
        local old=redis.call('GET',KEYS[2])
        if ARGV[2]=='1' then
          if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end
          redis.call('SET',KEYS[2],ARGV[3],'EX',25)
          redis.call('ZADD',KEYS[3],ARGV[4],ARGV[5])
          if old~=ARGV[3] then redis.call('PUBLISH',ARGV[6],'changed') end
        elseif old then
          local owner=cjson.decode(old)
          if tostring(owner.hostId)~=ARGV[7] or owner.sessionId~=ARGV[8] then return 0 end
          redis.call('DEL',KEYS[2]); redis.call('ZREM',KEYS[3],ARGV[5])
          redis.call('PUBLISH',ARGV[6],'changed')
        end
        return 1
        """,Long.class);
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Update {
        @NotNull @Positive private Long userId;
        @NotBlank @Pattern(regexp="[0-9a-f-]{36}") private String sessionId;
        @NotBlank @Pattern(regexp="[A-Z2-9]{8}") private String roomId;
        @NotBlank @Pattern(regexp="[0-9a-f-]{36}") private String runId;
        private boolean open;
        @Size(max=20) private String title;
        private boolean privateRoom;
    }
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Stored {
        private Long hostId;
        private String sessionId, roomId, runId, hostPublicId, nickname, avatar, title;
        private boolean privateRoom;
    }
    @Getter @Builder
    public static class Room {
        private final String id, title, hostPublicId, nickname, avatar;
        private final boolean friend;
        private final boolean privateRoom;
        private final int players;
    }
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Query {
        @Min(1) @Max(10000) private int page;
        @NotNull @Size(max=50) private String search;
        private boolean friendsOnly;
    }
    @Getter @Builder
    public static class Page {
        private final List<Room> items;
        private final int page, totalPages, total;
    }
    public void publish(Update request){
        var user=users.findById(request.userId).orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));
        var value=Stored.builder().hostId(request.userId).sessionId(request.sessionId).roomId(request.roomId).runId(request.runId)
            .hostPublicId(user.getPublicId()).nickname(user.getNickname()).avatar(user.getProfileAvatar().name())
            .title(request.title).privateRoom(request.privateRoom).build();
        // 원자 갱신 안에서 실제 표시 내용이 바뀐 경우에만 알린다. 하트비트는 조회 폭주를 만들지 않는다.
        redis.execute(PUBLISH,List.of("shooting:active:"+request.userId,key(request.roomId),INDEX),request.roomId+":"+request.sessionId,
            request.open?"1":"0",json.writeValueAsString(value),Long.toString(System.currentTimeMillis()+25_000),request.roomId,
            ShootingLobbyEvents.TOPIC,request.userId.toString(),request.sessionId);
    }
    public Page list(Long userId,Query query){
        if(!properties.enabled())throw new BusinessException(ErrorCode.SHOOTING_UNAVAILABLE);
        availability.requireNewPlay("SHOOTING","COOP");
        Long requests=redis.execute(RATE,List.of("shooting:limit:rooms:"+userId));
        if(requests==null||requests>90)throw new BusinessException(ErrorCode.SHOOTING_RATE_LIMIT);
        long now=System.currentTimeMillis();redis.opsForZSet().removeRangeByScore(INDEX,0,now);
        var ids=redis.opsForZSet().rangeByScore(INDEX,now,Double.POSITIVE_INFINITY,0,500);
        var values=ids==null||ids.isEmpty()?List.<String>of():redis.opsForValue().multiGet(ids.stream().map(this::key).toList());
        String search=query.search.trim().toLowerCase(Locale.ROOT);
        var stored=values==null?List.<Stored>of():values.stream().flatMap(raw->decode(raw).stream()).filter(s->!s.hostId.equals(userId)).toList();
        // Redis 임대와 친구 관계를 묶어서 읽어 공개 방 수만큼 네트워크·SQL 왕복이 늘지 않게 한다.
        var leases=stored.isEmpty()?List.<String>of():redis.opsForValue().multiGet(stored.stream().map(s->"shooting:active:"+s.hostId).toList());
        var friendIds=friends.friendIds(userId,stored.stream().map(Stored::getHostId).distinct().toList());
        var rooms=new ArrayList<Room>();
        for(int index=0;index<stored.size();index++){
            var s=stored.get(index);
            if(leases==null||!Objects.equals(leases.get(index),s.roomId+":"+s.sessionId))continue;
            String title=title(s);if(!title.toLowerCase(Locale.ROOT).contains(search)&&!s.nickname.toLowerCase(Locale.ROOT).contains(search))continue;
            boolean friend=friendIds.contains(s.hostId);if(query.friendsOnly&&!friend)continue;
            rooms.add(Room.builder().id(s.roomId).title(title).hostPublicId(s.hostPublicId).nickname(s.nickname).avatar(s.avatar).friend(friend).privateRoom(s.privateRoom).players(1).build());
        }
        rooms.sort(Comparator.comparing(Room::isFriend).reversed().thenComparing(Room::getTitle).thenComparing(Room::getId));
        int pages=Math.max(1,Math.ceilDiv(rooms.size(),4)),page=Math.clamp(query.page,1,pages),start=(page-1)*4;
        return Page.builder().items(rooms.subList(start,Math.min(start+4,rooms.size()))).page(page).totalPages(pages).total(rooms.size()).build();
    }
    @Override public String game(){return "shooting";}
    @Override public GameInvitationDtos.Room requireHost(Session session,String roomId){
        return available(roomId,session.getId(),session.getSessionId().toString()).orElseThrow(()->new BusinessException(ErrorCode.GAME_INVITATION_EXPIRED));
    }
    @Override public Optional<GameInvitationDtos.Room> available(String roomId,Long host,String session){
        if(!properties.enabled()||roomId==null||!roomId.matches("[A-Z2-9]{8}"))return Optional.empty();
        // 운영 중 비활성화된 모드는 초대를 수락한 뒤 입장이 거절되는 혼선을 만들지 않는다.
        if(!availability.canStart("SHOOTING","COOP"))return Optional.empty();
        return decode(redis.opsForValue().get(key(roomId))).filter(s->s.hostId.equals(host)&&s.sessionId.equals(session)&&live(s))
            .map(s->GameInvitationDtos.Room.builder().id(s.roomId).title(title(s)).version(s.runId).build());
    }
    @Override public boolean targetAvailable(Long user){return !Boolean.TRUE.equals(redis.hasKey("shooting:active:"+user));}
    private Optional<Stored> decode(String raw){
        if(raw==null)return Optional.empty();
        try{return Optional.of(json.readValue(raw,Stored.class));}catch(RuntimeException e){return Optional.empty();}
    }
    private boolean live(Stored s){return Objects.equals(redis.opsForValue().get("shooting:active:"+s.hostId),s.roomId+":"+s.sessionId);}
    private String title(Stored s){return s.title==null||s.title.isBlank()?s.nickname+"님의 방":s.title;}
    private String key(String room){return "shooting:lobby:"+room;}
}
