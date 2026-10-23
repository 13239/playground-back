package com.project.playground.domain.authorization.bootstrap;

import com.project.playground.domain.adminaudit.service.AdminAuditService;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.authorization.entity.Role;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** 비밀번호/회원 행을 생성하지 않고 지정한 기존 활성 계정만 최고 관리자로 배정한다. */
@Service
@RequiredArgsConstructor
public class SuperAdminBootstrapService {
    private final RoleRepository roles;
    private final UserRepository users;
    private final AdminAuditService audit;
    private final RedisSessionRepository sessions;

    @Transactional
    public Long initialize(String username) {
        // 여러 서버의 동시 초기화도 같은 보호 역할 행을 잠가 직렬화한다.
        roles.lockAuthorizationPolicy().orElseThrow(() -> new IllegalStateException("초기 역할 데이터가 없습니다."));
        var user = users.findForUpdateByUsername(username)
                .filter(value -> value.getDeletedAt() == null && value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("슈퍼 관리자 초기 배정 대상인 기존 활성 계정이 없습니다."));
        var existing = users.findByRoleCode(Role.SUPER_ADMIN);
        if (existing.isPresent()) {
            var owner = existing.get();
            // 아이디의 대소문자 비교도 실제 DB 조회 규칙을 따르고 최종 소유자는 회원 ID로 대조한다.
            if (!owner.getId().equals(user.getId()) || owner.getDeletedAt() != null || owner.getStatus() != UserStatus.ACTIVE) {
                throw new IllegalStateException("기존 슈퍼 관리자와 초기 설정이 일치하지 않습니다.");
            }
            return owner.getId();
        }
        String previousRole = user.getRoleCode();
        // 일반 회원 역할로 발급한 세션을 최고 관리자 세션으로 승격시키지 않는다.
        sessions.revokeAll(user.getId());
        user.initializeSuperAdmin();
        users.flush();
        audit.write(null, "SUPER_ADMIN_INITIALIZED", "USER", user.getId().toString(), "명시적 최고 관리자 초기 설정",
                Map.of("before", previousRole, "after", Role.SUPER_ADMIN));
        return user.getId();
    }
}

