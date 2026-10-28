package com.project.playground.domain.memory;

import com.project.playground.domain.game.service.GameAvailabilityService;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.global.exception.*;
import com.project.playground.global.security.session.Session;
import com.project.playground.auth.repository.RedisSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

/** 인증 주체·탭·현재 판과 서버 마감을 방 잠금 아래 검증한다. 클라이언트 점수는 사용하지 않는다. */
@Service @RequiredArgsConstructor @Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
public class MemoryService {
    private final MemoryRoomRepository rooms;
    private final MemoryStats stats;
    private final UserRepository users;
    private final GameAvailabilityService availability;
    private final PasswordEncoder passwords;
    private final MemoryRateLimit limits;
    private final MemoryEvents events;
    private final RedisSessionRepository sessions;
    private final Clock clock;
    private static final SecureRandom RANDOM = new SecureRandom();

    public MemoryDtos.Overview overview(Session session, MemoryDtos.Command command) {
        limits.acquire(session.getId(),"overview");
        int pages = Math.max(1,(int)Math.ceilDiv(stats.count(),10));
        int page = Math.clamp(command.getPage()==null?1:command.getPage(),1,pages);
        // 방 목록도 번호 페이지로 읽어 전체 대기방을 브라우저에 보내지 않는다.
        int roomPages = Math.max(1,(int)Math.ceilDiv(rooms.waitingCount(),10));
        int roomPage = Math.clamp(command.getRoomPage()==null?1:command.getRoomPage(),1,roomPages);
        var own = stats.own(session.getId());
        var current = rooms.current(session.getId());
        return MemoryDtos.Overview.builder().rankings(stats.ranking(session.getId(),page))
            .rooms(rooms.waiting(PageRequest.of(roomPage-1,10)).stream().map(r->MemoryDtos.RoomItem.builder()
                .roomId(r.getId()).title(r.getTitle()).privateRoom(r.getPasswordHash()!=null).players(r.getGuestId()==null?1:2).build()).toList())
            .page(page).totalPages(pages).roomPage(roomPage).roomPages(roomPages)
            .currentRoomId(current.isEmpty()?null:current.getFirst().getId()).bestMillis(own.getMillis())
            .rank(own.getMillis()==null?null:stats.rank(own.getMillis(),own.getAttempts())).build();
    }
    public MemoryDtos.State create(Session session, MemoryDtos.Command command, boolean solo) {
        limits.acquire(session.getId(),solo?"start":"create"); uuid(command.getRequestId()); uuid(command.getTabId());
        availability.requireNewPlay("MEMORY",solo?"SINGLE":"ONLINE"); lockUser(session.getId());
        var previous = rooms.findByRequestId(command.getRequestId());
        if (previous.isPresent()) {
            var room = rooms.lock(previous.get().getId()).orElseThrow();
            authorize(room,session,command.getTabId(),clock.instant());
            if (room.isClosed() || room.isSolo()!=solo) throw failure(ErrorCode.MEMORY_CHANGED);
            return view(room,session.getId());
        }
        for (var current : rooms.current(session.getId())) {
            if (current.isSolo() && "FINISHED".equals(current.getBoard().getPhase())) {
                var previousRoom=rooms.lock(current.getId()).orElseThrow();
                previousRoom.close();publish(previousRoom); // 새 도전으로 교체된 판의 접속 마감 예약도 즉시 해제한다.
            }
            else throw failure(ErrorCode.ROOM_ALREADY_JOINED);
        }
        String title = solo?"1인 기록 도전":Objects.toString(command.getTitle(),"").strip();
        if (title.isBlank() || title.codePointCount(0,title.length())>30) throw failure(ErrorCode.INVALID_REQUEST_BODY);
        String hash = null;
        if (!solo && Boolean.TRUE.equals(command.getPrivateRoom())) {
            if (command.getPassword()==null || command.getPassword().length()<4) throw failure(ErrorCode.ROOM_PASSWORD_REQUIRED);
            hash = passwords.encode(command.getPassword());
        }
        String code;
        do { code = String.format(Locale.ROOT,"%06X",RANDOM.nextInt(0x1000000)); } while (rooms.findByCode(code).isPresent());
        Instant now = clock.instant();
        var room = rooms.saveAndFlush(MemoryRoom.builder().requestId(command.getRequestId()).code(code).title(title)
            .passwordHash(hash).solo(solo).hostId(session.getId()).hostLogin(session.getSessionId().toString())
            .hostTab(command.getTabId()).now(now).build());
        if (solo) room.begin(false,now);
        publish(room); return view(room,session.getId());
    }
    public MemoryDtos.State join(Session session, MemoryDtos.Command command) {
        limits.acquire(session.getId(),"join"); uuid(command.getTabId());
        availability.requireNewPlay("MEMORY","ONLINE"); lockUser(session.getId());
        Long id = command.getRoomId();
        if (id==null) id=rooms.findByCode(Objects.toString(command.getCode(),"")).map(MemoryRoom::getId)
            .orElseThrow(()->failure(ErrorCode.ROOM_NOT_FOUND));
        var room = locked(id);
        if (room.seat(session.getId())>0) { authorize(room,session,command.getTabId(),clock.instant()); return view(room,session.getId()); }
        if (room.getPasswordHash()!=null && (command.getPassword()==null || !passwords.matches(command.getPassword(),room.getPasswordHash())))
            throw failure(ErrorCode.ROOM_PASSWORD_INVALID);
        joinInvited(room,session.getId(),session.getSessionId().toString(),command.getTabId());
        return view(room,session.getId());
    }
    /** 유효한 본인 초대 수락만 비밀번호 대신 사용할 수 있다. 정원·기존 참가 검사는 동일하다. */
    public void joinInvited(MemoryRoom room, Long user, String login, String tab) {
        availability.requireNewPlay("MEMORY","ONLINE");
        if (room.isClosed() || room.isSolo() || !"WAITING".equals(room.getBoard().getPhase())) throw failure(ErrorCode.ROOM_CLOSED);
        if (room.getGuestId()!=null) throw failure(ErrorCode.ROOM_FULL);
        if (!rooms.current(user).isEmpty()) throw failure(ErrorCode.ROOM_ALREADY_JOINED);
        room.join(user,login,tab,clock.instant()); publish(room);
    }
    public MemoryDtos.State state(Session session, MemoryDtos.Command command) {
        limits.acquire(session.getId(),"state");
        var room=locked(command.getRoomId()); Instant now=clock.instant();
        long previous=room.getRevision();
        advance(room,now); authorize(room,session,command.getTabId(),now);
        if(previous!=room.getRevision())events.changed(room.getId());
        events.clockChanged(room.getId(),room.nextDeadline());
        return view(room,session.getId());
    }
    public MemoryDtos.State action(Session session, MemoryDtos.Command c, String action) {
        limits.acquire(session.getId(),action); uuid(c.getRequestId());
        var room=locked(c.getRoomId()); Instant now=clock.instant();
        advance(room,now); int seat=authorize(room,session,c.getTabId(),now);
        var board=room.getBoard();
        if (c.getRevision()==null || c.getRevision()!=room.getRevision()) return view(room,session.getId());
        if (!"ready".equals(action) && !Objects.equals(c.getMatchId(),board.getMatchId())) return view(room,session.getId());
        if ("leave".equals(action)) {
            if (board.active()) { board.finish(room.isSolo()?0:3-seat,"RESIGN",now); room.changed(); record(room); }
            room.leave(seat); publish(room); return view(room,session.getId());
        }
        if (room.isClosed()) throw failure(ErrorCode.ROOM_CLOSED);
        // revision을 포함한 동일 요청의 재전송은 상태 충돌로만 처리되며 점수·전적은 재반영되지 않는다.
        switch (action) {
            case "ready" -> {
                if (!"WAITING".equals(board.getPhase()) || room.getGuestId()==null) throw failure(ErrorCode.ROOM_NOT_FULL);
                room.ready(seat,Boolean.TRUE.equals(c.getReady()));
                if (room.isHostReady() && room.isGuestReady()) { availability.requireNewPlay("MEMORY","ONLINE"); room.begin(false,now); }
            }
            case "flip" -> {
                if (!Objects.equals(c.getMatchId(),board.getMatchId())) return view(room,session.getId());
                if (c.getPosition()==null || !board.canFlip(room.isSolo(),seat,c.getPosition())) throw failure(ErrorCode.MEMORY_CHANGED);
                board.flip(room.isSolo(),seat,c.getPosition(),now); room.changed(); record(room);
            }
            case "rematch" -> {
                if (room.isSolo() || room.getGuestId()==null || !"FINISHED".equals(board.getPhase())) throw failure(ErrorCode.MEMORY_CHANGED);
                availability.requireNewPlay("MEMORY","ONLINE"); room.rematch(seat);
                if (room.isHostRematch() && room.isGuestRematch()) room.begin(true,now);
            }
            default -> throw failure(ErrorCode.INVALID_REQUEST_BODY);
        }
        publish(room); return view(room,session.getId());
    }
    public void tick(Long id) {
        var room=rooms.lock(id).orElse(null);
        if (room==null || room.isClosed()) return;
        advance(room,clock.instant()); publish(room);
    }
    private void advance(MemoryRoom room, Instant now) {
        if (room.isClosed()) return;
        // 브라우저 단절은 60초 유예한다. 양쪽 단절·서버 장기 중단은 무효이며 승패를 만들지 않는다.
        boolean hostGone=!now.isBefore(room.getHostSeen().plusSeconds(room.isSolo()?7200:60));
        boolean guestGone=room.getGuestSeen()!=null && !now.isBefore(room.getGuestSeen().plusSeconds(60));
        if (hostGone || guestGone) {
            if (room.getBoard().active()) {
                boolean validWinner=!room.isSolo() && hostGone!=guestGone && room.getGuestId()!=null;
                room.getBoard().finish(validWinner?(hostGone?2:1):0,validWinner?"DISCONNECT":"INTERRUPTED",now);
                room.changed(); record(room);
            }
            room.close(); return;
        }
        if (room.getBoard().advance(room.isSolo(),now)) room.changed();
    }
    private void record(MemoryRoom room) {
        var board=room.getBoard();
        if (!"FINISHED".equals(board.getPhase()) || board.isRecorded()) return;
        if (room.isSolo() && "COMPLETE".equals(board.getFinishReason()))
            stats.completed(room.getHostId(),Duration.between(board.getStartedAt(),board.getFinishedAt()).toMillis(),board.getAttempts(),board.getFinishedAt());
        if (!room.isSolo() && board.getWinner()>0) {
            // 서로 다른 경기의 통계 갱신도 항상 회원 번호 순서로 수행한다.
            List.of(room.getHostId(),room.getGuestId()).stream().sorted().forEach(id->stats.result(id,room.seat(id)==board.getWinner()));
        }
        board.markRecorded();
    }
    private int authorize(MemoryRoom room, Session session, String tab, Instant now) {
        uuid(tab); int seat=room.seat(session.getId());
        if (seat==0) throw failure(ErrorCode.ROOM_NOT_JOINED);
        if (!Objects.equals(room.tab(seat),tab) && sessions.findBySessionId(UUID.fromString(room.login(seat)),session.getId()).isPresent())
            throw failure(ErrorCode.ROOM_PAGE_IN_USE);
        room.touch(seat,session.getSessionId().toString(),tab,now); return seat;
    }
    private MemoryDtos.State view(MemoryRoom room, Long user) {
        var b=room.getBoard(); Instant now=clock.instant();
        return MemoryDtos.State.builder().roomId(room.getId()).title(room.getTitle()).code(room.getCode()).solo(room.isSolo())
            .privateRoom(room.getPasswordHash()!=null).closed(room.isClosed()).revision(room.getRevision()).matchId(b.getMatchId())
            .phase(b.getPhase()).serverTime(now).startedAt(b.getStartedAt()).deadline(b.getDeadline()).finishedAt(b.getFinishedAt())
            .seat(room.seat(user)).turn(b.getTurn()).scoreOne(b.getScoreOne()).scoreTwo(b.getScoreTwo()).attempts(b.getAttempts())
            .winner(b.getWinner()).finishReason(b.getFinishReason()).cards(b.visible(now)).owners(b.getOwners())
            .one(player(room.getHostId(),user,room.isHostReady(),room.isHostRematch()))
            .two(room.getGuestId()==null?null:player(room.getGuestId(),user,room.isGuestReady(),room.isGuestRematch())).build();
    }
    private MemoryDtos.Player player(Long id, Long self, boolean ready, boolean rematch) {
        var user=users.findById(id).orElseThrow(); var own=stats.own(id);
        return MemoryDtos.Player.builder().nickname(user.getNickname()).avatarId(user.getProfileAvatar().name()).self(id.equals(self))
            .ready(ready).rematch(rematch).wins(own.getWins()).losses(own.getLosses()).build();
    }
    private void publish(MemoryRoom room) { events.changed(room.getId()); events.clockChanged(room.getId(),room.nextDeadline()); }
    public MemoryRoom locked(Long id) { if(id==null)throw failure(ErrorCode.INVALID_REQUEST_BODY); return rooms.lock(id).orElseThrow(()->failure(ErrorCode.ROOM_NOT_FOUND)); }
    public void lockUser(Long id) { var user=users.findForUpdateById(id).orElseThrow(()->failure(ErrorCode.ACCESS_DENIED)); if(user.getStatus()!=UserStatus.ACTIVE || user.getDeletedAt()!=null)throw failure(ErrorCode.ACCESS_DENIED); }
    public static String uuid(String value) { try { if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException(); return value; } catch(RuntimeException e){throw failure(ErrorCode.INVALID_REQUEST_BODY);} }
    private static BusinessException failure(ErrorCode code) { return new BusinessException(code); }
}
