package com.project.playground.domain.memory;

import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 모든 경로는 기존 BFF·JWT·브라우저 증명 필터를 공유하고 응답은 캐시하지 않는다. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/memory")
public class MemoryController {
    private final MemoryService games;
    private final MemoryEvents events;
    private final MemoryRateLimit limits;
    private final MemoryInvitationService invitations;
    @PostMapping("/overview") public ResponseEntity<MemoryDtos.Overview> overview(@AuthenticationPrincipal(errorOnInvalidType=true)Session s,@Valid @RequestBody MemoryDtos.Command c){return ok(games.overview(s,c));}
    @PostMapping("/{operation:start|create}") public ResponseEntity<MemoryDtos.State> create(@AuthenticationPrincipal(errorOnInvalidType=true)Session s,@Valid @RequestBody MemoryDtos.Command c,@PathVariable String operation){return ok(games.create(s,c,operation.equals("start")));}
    @PostMapping("/join") public ResponseEntity<MemoryDtos.State> join(@AuthenticationPrincipal(errorOnInvalidType=true)Session s,@Valid @RequestBody MemoryDtos.Command c){return ok(games.join(s,c));}
    @PostMapping("/state") public ResponseEntity<MemoryDtos.State> state(@AuthenticationPrincipal(errorOnInvalidType=true)Session s,@Valid @RequestBody MemoryDtos.Command c){return ok(games.state(s,c));}
    @PostMapping("/{action:ready|flip|leave|rematch}") public ResponseEntity<MemoryDtos.State> action(@AuthenticationPrincipal(errorOnInvalidType=true)Session s,@Valid @RequestBody MemoryDtos.Command c,@PathVariable String action){return ok(games.action(s,c,action));}
    @PostMapping(value="/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@AuthenticationPrincipal(errorOnInvalidType=true)Session s,@Valid @RequestBody MemoryDtos.Command c){
        limits.acquire(s.getId(),"events");var state=games.state(s,c);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no")
            .body(events.subscribe(s.getId(),s.getSessionId(),c.getRoomId(),state.getRevision()));
    }
    @PostMapping("/invitations/send") public ResponseEntity<MemoryDtos.Invitation> invite(@AuthenticationPrincipal(errorOnInvalidType=true)Session s,@Valid @RequestBody MemoryDtos.Command c){return ok(invitations.send(s,c));}
    @PostMapping("/invitations/{action:accept|reject|cancel}") public ResponseEntity<MemoryDtos.Decision> answer(@AuthenticationPrincipal(errorOnInvalidType=true)Session s,@Valid @RequestBody MemoryDtos.Command c,@PathVariable String action){return ok(invitations.respond(s,c,action));}
    private <T>ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
