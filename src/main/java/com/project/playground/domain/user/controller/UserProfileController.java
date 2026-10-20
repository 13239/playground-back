package com.project.playground.domain.user.controller;

import com.project.playground.domain.user.dto.request.UpdateProfileRequest;
import com.project.playground.domain.user.dto.response.UserProfileResponse;
import com.project.playground.domain.user.service.UserProfileService;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BFF와 같은 경로를 사용해 기존 JWT·Redis 세션·브라우저 요청 서명 검증을 그대로 적용한다. */
@RestController
@RequestMapping("/api/auth/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(userProfileService.getProfile(session.getId()));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody UpdateProfileRequest request) {
        // 수정 대상은 검증된 세션에서만 가져오므로 다른 회원의 ID를 지정할 수 없다.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(userProfileService.updateProfile(session.getId(), request));
    }
}
