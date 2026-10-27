package com.project.playground.domain.social;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import com.project.playground.global.security.session.Session;

/** 기존 JWT·브라우저 서명·BFF 경계를 그대로 사용하는 본인 전용 API다. */
@RestController @RequestMapping("/api/social") @RequiredArgsConstructor
public class SocialController {
    private final SocialService service;
    @PostMapping("/snapshot")
    public ResponseEntity<SocialDtos.Snapshot> snapshot(@AuthenticationPrincipal(errorOnInvalidType=true) Session session,@RequestBody SocialDtos.Command command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.snapshot(session.getId(),command));
    }
    @PostMapping("/profile")
    public ResponseEntity<SocialDtos.Person> profile(@AuthenticationPrincipal(errorOnInvalidType=true) Session session,@RequestBody SocialDtos.Command command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.profile(session.getId(),command.getTargetId()));
    }
    @PostMapping("/{action}")
    public ResponseEntity<Object> command(@AuthenticationPrincipal(errorOnInvalidType=true) Session session,@PathVariable String action,@RequestBody SocialDtos.Command command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.command(session.getId(),action,command));
    }
    @PostMapping("/request-location")
    public ResponseEntity<SocialDtos.RequestLocation> location(@AuthenticationPrincipal(errorOnInvalidType=true) Session session,@RequestBody SocialDtos.Command command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.requestLocation(session.getId(),command.getRequestId()));
    }
}

