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
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String DEFAULT_ROLE = "USER";

    @Id
    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "system_role", nullable = false, updatable = false)
    private boolean systemRole;

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

    public boolean isSuperAdmin() {
        return SUPER_ADMIN.equals(code);
    }

    /** 슈퍼 관리자 행은 공통 정책 잠금에도 사용하므로 일반 편집으로 바꾸지 않는다. */
    public void update(String name, String description, Set<PermissionCode> permissions) {
        assertEditable();
        this.name = name;
        this.description = description;
        replacePermissions(permissions);
    }

    public void assertDeletable() {
        if (systemRole || isSuperAdmin()) throw new BusinessException(ErrorCode.ROLE_PROTECTED);
    }

    private void assertEditable() {
        if (isSuperAdmin()) throw new BusinessException(ErrorCode.ROLE_PROTECTED);
    }

    private void replacePermissions(Set<PermissionCode> requested) {
        if (requested == null || requested.stream().anyMatch(value -> value == null || value.isSuperAdminOnly())) {
            throw new BusinessException(ErrorCode.ROLE_PERMISSION_NOT_ASSIGNABLE);
        }
        permissions.clear();
        permissions.addAll(requested);
    }
}

