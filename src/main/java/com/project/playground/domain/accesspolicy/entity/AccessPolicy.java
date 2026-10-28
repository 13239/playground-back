package com.project.playground.domain.accesspolicy.entity;

import com.project.playground.domain.authorization.entity.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** 하나의 정책 버전으로 허용 역할과 과거 세션 폐기 경계를 함께 변경한다. */
@Entity
@Table(name = "tbl_access_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessPolicy {
    private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");
    @Id
    private Long id;
    @Column(nullable = false)
    private boolean enabled;
    // 회원가입 제한은 로그인 허용 역할·기존 세션 폐기와 독립적으로 저장한다.
    @Column(name = "sign_up_restricted", nullable = false)
    private boolean signUpRestricted;
    @Column(nullable = false, length = 300)
    private String message;
    @Version
    private long version;
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Getter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "tbl_access_policy_exemption", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "role_code", length = 64, nullable = false)
    private Set<String> exemptRoleCodes = new HashSet<>();

    @Getter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "tbl_access_policy_revocation", joinColumns = @JoinColumn(name = "policy_id"))
    @MapKeyColumn(name = "role_code", length = 64)
    @Column(name = "revoked_before_version", nullable = false)
    private Map<String, Long> revocations = new HashMap<>();

    public Set<String> getExemptRoleCodes() { return Set.copyOf(exemptRoleCodes); }
    public Map<String, Long> getRevocations() { return Map.copyOf(revocations); }

    /** 가입 제한 변경도 동일한 정책 버전과 감사 기록 안에서 저장한다. */
    public void changeSignUpRestriction(boolean restricted) {
        signUpRestricted = restricted;
    }

    /** ROOT는 설정 목록과 관계없이 허용한다. 새 제한은 이전 로그인을 영구 무효화한다. */
    public void change(boolean enabled, String message, Set<String> exemptions, Collection<String> existingRoles) {
        this.enabled = enabled;
        // 저장한 안내를 로그인 확인창에 그대로 전달하도록 앞뒤 공백도 변경하지 않는다.
        this.message = message;
        exemptRoleCodes.clear();
        exemptRoleCodes.addAll(exemptions);
        exemptRoleCodes.remove(Role.ROOT);
        if (enabled) {
            long nextVersion = Math.incrementExact(version);
            existingRoles.stream().filter(code -> !Role.ROOT.equals(code) && !exemptRoleCodes.contains(code))
                    .forEach(code -> revocations.put(code, nextVersion));
        }
        // 동일 값 재적용도 별도 운영 명령이므로 부모 행을 갱신해 컬렉션과 같은 버전을 부여한다.
        changedAt = LocalDateTime.now(POLICY_ZONE);
    }
}
