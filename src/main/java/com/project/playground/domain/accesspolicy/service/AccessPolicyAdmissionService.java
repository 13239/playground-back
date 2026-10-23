package com.project.playground.domain.accesspolicy.service;

import com.project.playground.auth.dto.internal.LoginSessionReplacement;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.accesspolicy.repository.AccessPolicyRepository;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.session.RedisSession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 검증 이후의 짧은 발급 구간만 공유 잠금으로 보호하여 여러 계정의 로그인을 병렬 처리한다. */
@Service @RequiredArgsConstructor
public class AccessPolicyAdmissionService {
    private final RoleRepository roles;
    private final UserRepository users;
    private final AccessPolicyRepository policies;
    private final RedisSessionRepository sessions;

    @Transactional
    public String create(RedisSession session, @Nullable LoginSessionReplacement replacement, String encryptedResult, boolean replace) {
        roles.lockAuthorizationPolicyForAdmission().orElseThrow();
        var user = users.findForShareById(session.getId())
                .filter(value -> value.getStatus() == UserStatus.ACTIVE && value.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        var policy = policies.findById(1L).orElseThrow();
        return sessions.create(session, replacement, encryptedResult, replace, user.getRoleCode(), policy.getVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
    }
}
