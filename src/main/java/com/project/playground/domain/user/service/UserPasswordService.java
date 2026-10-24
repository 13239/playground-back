package com.project.playground.domain.user.service;

import com.project.playground.auth.ratelimit.PasswordChangeRateLimitException;
import com.project.playground.auth.repository.RedisLoginRateLimitRepository;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.user.dto.request.ChangePasswordRequest;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.session.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** 본인 비밀번호를 재검증하고 현재 로그인은 유지한다. 원문은 저장하거나 기록하지 않는다. */
@Service
@RequiredArgsConstructor
public class UserPasswordService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RedisSessionRepository sessions;
    private final RedisLoginRateLimitRepository rateLimits;

    @Transactional
    public void changePassword(Session session, ChangePasswordRequest request) {
        // 비싼 해시 계산·행 잠금 전에 계정당 분당 5회로 제한하고 성공 요청도 이 예산을 사용한다.
        long remaining = rateLimits.acquire("password-change:account:" + session.getId(), 5, Duration.ofMinutes(1));
        if (remaining > 0) throw new PasswordChangeRateLimitException(Math.ceilDiv(remaining, 1_000L));

        var user = users.findForUpdateById(session.getId())
                .filter(value -> value.getDeletedAt() == null && value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 잠금을 기다리는 사이 다른 변경이 로그인을 종료했다면 이전 인증으로 다시 변경하지 않는다.
        sessions.findBySessionId(session.getSessionId(), session.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));
        if (!passwords.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_UNCHANGED);
        }

        user.changePassword(passwords.encode(request.getNewPassword()));
        users.flush();
        // 현재 Redis 세션·토큰·만료 시각은 유지한다. 새 로그인은 발급 직전 해시 대조로 이전 비밀번호를 거절한다.
    }
}
