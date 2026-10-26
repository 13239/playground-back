package com.project.playground.domain.authorization.entity;

import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 역할 자체는 DB 데이터이며 기능 매핑은 변경 버전을 공유하는 한 편집 단위다. */
@Entity
@Table(name = "tbl_role")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {
    public static final String ROOT = "ROOT";
    public static final String DEFAULT_ROLE = "USER";

    @Id
    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    // 처음 제공한 역할은 DB의 0~3 순서를 사용하고 추가 역할은 그 뒤에서 코드순으로 표시한다.
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 4;

    @Version
    // null 버전으로 신규 엔티티를 식별해 코드가 있는 새 역할도 merge가 아닌 persist로 생성한다.
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 단방향 값 컬렉션으로 기능 사전의 생명주기를 역할 수정과 분리한다.
    @Getter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "tbl_role_permission", joinColumns = @JoinColumn(name = "role_code"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_code", nullable = false, length = 64, columnDefinition = "varchar(64)")
    private Set<PermissionCode> permissions = new HashSet<>();

    @Builder
    public Role(String code, String name, String description, Set<PermissionCode> permissions) {
        this.code = code;
        this.name = name;
        this.description = description;
        replacePermissions(permissions);
    }

    /** 외부에서 컬렉션을 직접 변경하여 검증이나 변경 버전을 우회하지 못하게 한다. */
    public Set<PermissionCode> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public boolean isRoot() {
        // 최고 관리자 판별은 변경할 수 없는 ROOT 코드만 사용하며 별도 DB 플래그에 의존하지 않는다.
        return ROOT.equals(code);
    }

    /** ROOT 행은 공통 정책 잠금에도 사용하므로 일반 편집으로 바꾸지 않는다. */
    public void update(String name, String description, Set<PermissionCode> permissions) {
        assertEditable();
        this.name = name;
        this.description = description;
        replacePermissions(permissions);
    }

    /** ROOT는 사용 여부와 관계없이 삭제할 수 없으며 다른 역할의 사용 여부는 서비스에서 검사한다. */
    public void assertDeletable() {
        if (isRoot()) throw new BusinessException(ErrorCode.ROLE_PROTECTED);
    }

    private void assertEditable() {
        if (isRoot()) throw new BusinessException(ErrorCode.ROLE_PROTECTED);
    }

    private void replacePermissions(Set<PermissionCode> requested) {
        if (requested == null || requested.stream().anyMatch(value -> value == null || value.isSuperAdminOnly())) {
            throw new BusinessException(ErrorCode.ROLE_PERMISSION_NOT_ASSIGNABLE);
        }
        permissions.clear();
        permissions.addAll(requested);
        // 수정 기능만 단독 배정해 화면/API에 접근할 수 없는 역할이 저장되지 않게 한다.
        requested.forEach(permission -> permissions.addAll(permission.getRequiredPermissions()));
    }
}

