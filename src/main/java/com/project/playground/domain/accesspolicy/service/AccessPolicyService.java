package com.project.playground.domain.accesspolicy.service;

import com.project.playground.domain.accesspolicy.dto.*;
import com.project.playground.domain.accesspolicy.entity.AccessPolicy;
import com.project.playground.domain.accesspolicy.redis.RedisAccessPolicyRepository;
import com.project.playground.domain.accesspolicy.repository.AccessPolicyRepository;
import com.project.playground.domain.adminaudit.service.AdminAuditService;
import com.project.playground.domain.authorization.entity.Role;
import com.project.playground.domain.authorization.dto.response.RolePageResponse;
import com.project.playground.domain.authorization.dto.response.RoleSummaryResponse;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/** 정책 명령은 권한 재확인·버전 충돌·집행 보류·감사 기록을 묶고 커밋 후에 집행을 확정한다. */
@Service @RequiredArgsConstructor
public class AccessPolicyService {
    private final TransactionTemplate transactions;
    private final AdminAuthorizationService authorization;
    private final RoleRepository roles;
    private final AccessPolicyRepository policies;
    private final RedisAccessPolicyRepository redis;
    private final AccessPolicySynchronizer synchronizer;
    private final AdminAuditService audit;

    @Transactional(readOnly = true)
    public AccessPolicyResponse get(Long actorId) {
        authorization.require(actorId, PermissionCode.ACCESS_POLICY_READ);
        return response(policies.findById(1L).orElseThrow());
    }

    public AccessPolicyResponse update(Long actorId, UpdateAccessPolicyRequest request) {
        return change(actorId, request.getExpectedVersion(), request.getEnabled(), request.getSignUpRestricted(), request.getMessage(), null, request.getReason());
    }

    public AccessPolicyResponse exemptions(Long actorId, UpdateAccessExemptionsRequest request) {
        return change(actorId, request.getExpectedVersion(), null, null, null, request.getRoleCodes(), request.getReason());
    }

    /** ROOT는 집계에서 제외하고 실제 페이지 크기만 조회한다. 한글 검색은 UTF-8 이름에만 비교한다. */
    @Transactional(readOnly = true)
    public RolePageResponse roleOptions(Long actorId, String query, int page, int size) {
        authorization.require(actorId, PermissionCode.ACCESS_EXEMPTION_UPDATE);
        if (page < 1 || !Set.of(10, 20, 30, 50).contains(size)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        String search = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        boolean nameOnly = search != null && !query.strip().chars().allMatch(value -> value <= 0x7f);
        var result = nameOnly ? roles.searchAccessNamePage(search, PageRequest.of(page - 1, size))
                : roles.searchAccessPage(search, PageRequest.of(page - 1, size));
        int totalPages = Math.max(1, result.getTotalPages());
        int current = Math.min(page, totalPages);
        // 삭제·동시 편집으로 마지막 페이지가 줄어도 빈 범위에 머무르지 않는다.
        if (current != page) result = nameOnly ? roles.searchAccessNamePage(search, PageRequest.of(current - 1, size))
                : roles.searchAccessPage(search, PageRequest.of(current - 1, size));
        return RolePageResponse.builder().items(result.getContent().stream().map(RoleSummaryResponse::from).toList())
                .page(current).size(size).totalPages(totalPages).totalElements(result.getTotalElements()).build();
    }

    private AccessPolicyResponse change(Long actorId, long expected, Boolean enabled, Boolean signUpRestricted, String message, Set<String> exemptions, String reason) {
        var result = transactions.execute(status -> {
            roles.lockAuthorizationPolicy().orElseThrow();
            var actor = authorization.require(actorId, exemptions == null
                    ? PermissionCode.ACCESS_POLICY_UPDATE : PermissionCode.ACCESS_EXEMPTION_UPDATE);
            var policy = policies.findById(1L).orElseThrow();
            if (policy.getVersion() != expected) throw new BusinessException(ErrorCode.AUTHORIZATION_CHANGED);
            if (exemptions != null && exemptions.stream().anyMatch(code -> Role.ROOT.equals(code) || !roles.existsById(code))) {
                throw new BusinessException(ErrorCode.ACCESS_EXEMPTION_INVALID);
            }
            var before = snapshot(policy);
            var roleCodes = roles.findAll().stream().map(Role::getCode).toList();
            redis.freeze();
            if (signUpRestricted != null) policy.changeSignUpRestriction(signUpRestricted);
            policy.change(enabled == null ? policy.isEnabled() : enabled, message == null ? policy.getMessage() : message,
                    exemptions == null ? policy.getExemptRoleCodes() : exemptions, roleCodes);
            policies.flush();
            audit.write(actor, exemptions == null ? "ACCESS_POLICY_UPDATED" : "ACCESS_EXEMPTIONS_UPDATED", "ACCESS_POLICY", "1", reason,
                    Map.of("before", before, "after", snapshot(policy)));
            return response(policy);
        });
        // 별도 트랜잭션으로 잠금을 다시 얻으므로 커밋 실패·동시 편집·다른 서버의 복구와 순서가 뒤바뀌지 않는다.
        try { synchronizer.synchronize(); }
        catch (RuntimeException exception) { throw new BusinessException(ErrorCode.ACCESS_POLICY_UNAVAILABLE); }
        return result;
    }

    // 선택한 역할만 한 번에 읽으며 각 역할의 기능 컬렉션은 로딩하지 않는다.
    private AccessPolicyResponse response(AccessPolicy policy) {
        return AccessPolicyResponse.from(policy, roles.findAllById(policy.getExemptRoleCodes()));
    }

    private static Map<String, Object> snapshot(AccessPolicy policy) {
        return Map.of("enabled", policy.isEnabled(), "signUpRestricted", policy.isSignUpRestricted(), "message", policy.getMessage(),
                "exemptRoleCodes", policy.getExemptRoleCodes().stream().sorted().toList(), "version", policy.getVersion());
    }
}
