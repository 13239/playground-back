package com.project.playground.domain.gameinvite;

import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 공통 초대 API도 기존 BFF·세션·브라우저 증명을 통과해야 하며 발신자는 인증 주체로 결정한다. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/game-invitations")
public class GameInvitationController {
    private final GameInvitationService invitations;
    @PostMapping("/send")
    public ResponseEntity<GameInvitationDtos.Invitation> send(@AuthenticationPrincipal(errorOnInvalidType=true) Session session,@Valid @RequestBody GameInvitationDtos.Command command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.send(session,command));
    }
    @PostMapping("/{action:accept|reject|cancel}")
    public ResponseEntity<GameInvitationDtos.Decision> respond(@AuthenticationPrincipal(errorOnInvalidType=true) Session session,@Valid @RequestBody GameInvitationDtos.Command command,@PathVariable String action){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.respond(session,command,action));
    }
}
