package com.project.playground.domain.accesspolicy.dto;

import com.project.playground.domain.accesspolicy.entity.AccessPolicy;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Set;

/** 화면에는 운영 설정만 제공하며 내부 세션 폐기 경계는 노출하지 않는다. */
@Getter @Builder
public class AccessPolicyResponse {
    private final boolean enabled;
    private final String message;
    private final Set<String> exemptRoleCodes;
    private final long version;
    private final LocalDateTime changedAt;

    public static AccessPolicyResponse from(AccessPolicy policy) {
        return builder().enabled(policy.isEnabled()).message(policy.getMessage())
                .exemptRoleCodes(policy.getExemptRoleCodes()).version(policy.getVersion()).changedAt(policy.getChangedAt()).build();
    }
}
