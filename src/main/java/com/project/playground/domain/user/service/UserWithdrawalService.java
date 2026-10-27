package com.project.playground.domain.user.service;

import com.project.playground.auth.ratelimit.AccountWithdrawalRateLimitException;
import com.project.playground.auth.repository.RedisLoginRateLimitRepository;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.activitylog.enums.ActivityType;
import com.project.playground.domain.activitylog.service.ActivityLogger;
import com.project.playground.domain.user.dto.request.WithdrawAccountRequest;
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
import java.util.Map;

/** 본인 비밀번호를 재검증한 뒤 기존 상태 변경·전체 세션 차단을 같은 트랜잭션에서 처리한다. */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RedisSessionRepository sessions;
    private final RedisLoginRateLimitRepository rateLimits;
    private final UserService accounts;
    private final ActivityLogger activity;

    @Transactional
    public void withdraw(Session session, WithdrawAccountRequest request) {
        // 비싼 비밀번호 대조와 행 잠금 전에 계정당 분당 5회로 제한한다.
        long remaining = rateLimits.acquire("account-withdrawal:account:" + session.getId(), 5, Duration.ofMinutes(1));
        if (remaining > 0) throw new AccountWithdrawalRateLimitException(Math.ceilDiv(remaining, 1_000L));

        var user = users.findForUpdateById(session.getId())
                .filter(value -> value.getDeletedAt() == null && value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.assertNotSuperAdmin();
        // 잠금을 기다리는 동안 종료·교체된 세션으로 다른 변경을 실행하지 않는다.
        sessions.findBySessionId(session.getSessionId(), session.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));
        if (!passwords.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }

        // WITHDRAWN 전환과 로그인 차단을 재사용하고 deleted_at은 기록하지 않는다.
        accounts.withdraw(session.getId());
        users.flush();
        // 비밀번호·토큰은 남기지 않으며 활동 기록은 기존 커밋 이후 저장 경로를 사용한다.
        activity.success(ActivityType.WITHDRAWAL, session.getId(), Map.of());
    }
}
