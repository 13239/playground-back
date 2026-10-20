package com.project.playground.domain.user.controller;

import com.project.playground.auth.clientip.LoginClientIpResolver;
import com.project.playground.auth.ratelimit.SignUpRateLimitService;
import com.project.playground.domain.user.dto.request.SignUpRequest;
import com.project.playground.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final LoginClientIpResolver clientIpResolver;

    private final SignUpRateLimitService signUpRateLimitService;

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public void signUp(@Valid @RequestBody SignUpRequest request, HttpServletRequest servletRequest) {
        // 기존 신뢰 프록시 판별을 공유하고 비밀번호 계산 전에 가입 전용 요청 제한을 적용한다.
        signUpRateLimitService.beforeSignUp(clientIpResolver.resolve(servletRequest));
        userService.signUp(request);
    }
}
