package com.project.playground.domain.accesspolicy.dto;

import com.project.playground.domain.accesspolicy.entity.AccessPolicy;
import com.project.playground.domain.authorization.dto.response.RoleSummaryResponse;
import com.project.playground.domain.authorization.entity.Role;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;

/** 화면에는 운영 설정만 제공하며 내부 세션 폐기 경계는 노출하지 않는다. */
@Getter @Builder
public class AccessPolicyResponse {
    private final boolean enabled;
    private final boolean signUpRestricted;
    private final String message;
    private final Set<String> exemptRoleCodes;
    // 다른 검색·페이지에 있는 저장된 선택도 이름과 코드로 즉시 표시한다.
    private final List<RoleSummaryResponse> exemptRoles;
    private final long version;
    private final LocalDateTime changedAt;

    public static AccessPolicyResponse from(AccessPolicy policy, List<Role> exemptions) {
        return builder().enabled(policy.isEnabled()).signUpRestricted(policy.isSignUpRestricted()).message(policy.getMessage())
                .exemptRoles(exemptions.stream().map(RoleSummaryResponse::from).toList())
                .exemptRoleCodes(policy.getExemptRoleCodes()).version(policy.getVersion()).changedAt(policy.getChangedAt()).build();
    }
}
