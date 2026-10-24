package com.project.playground.domain.user.entity;

import com.project.playground.domain.user.enums.Gender;
import com.project.playground.domain.user.enums.ProfileAvatar;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.authorization.entity.Role;
import com.project.playground.global.audit.TimeEntity;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Entity
// 동시 가입의 아이디 충돌을 식별할 수 있도록 새 스키마의 고유 제약 이름을 고정한다.
@Table(name = "tbl_user", uniqueConstraints = @UniqueConstraint(name = "uk_tbl_user_username", columnNames = "username"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends TimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nickname;

    // 기존 회원은 DDL 기본값으로, 신규 회원은 엔티티 기본값으로 고양이 프로필을 사용한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_avatar", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private ProfileAvatar profileAvatar = ProfileAvatar.CAT;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column
    private LocalDate birthday;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "char(1)")
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status;

    // 가입 입력으로 역할을 받지 않으며 모든 신규 회원은 USER로 시작한다.
    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode = Role.DEFAULT_ROLE;

    // 프로필 편집과 분리해 관리자 역할 배정의 오래된 요청만 감지한다.
    @Column(name = "role_version", nullable = false)
    private long roleVersion;

    // 상태·프로필·역할 변경을 포함한 오래된 관리자 편집을 거절하며 기존 역할 전용 버전은 유지한다.
    @Version
    @Column(name = "management_version", nullable = false)
    private long managementVersion;

    @Builder
    public User(String nickname, String username, String password, String email, String phone, LocalDate birthday, Gender gender, UserStatus status) {
        this.nickname = nickname;
        this.username = username;
        this.password = password;
        this.email = email;
        this.phone = phone;
        this.birthday = birthday;
        this.gender = gender;
        this.status = status;
    }

    /** 고정된 캐릭터만 선택할 수 있으며 프로필 변경으로 다른 회원 정보는 수정하지 않는다. */
    public void changeProfileAvatar(ProfileAvatar profileAvatar) {
        this.profileAvatar = Objects.requireNonNull(profileAvatar, "프로필 캐릭터는 필수입니다.");
    }

    /** 검증된 본인 닉네임만 변경하며 역할·아이디·프로필 이미지는 유지한다. */
    public void changeNickname(String nickname) {
        this.nickname = Objects.requireNonNull(nickname, "닉네임은 필수입니다.");
    }

    /** 비밀번호 검증 서비스가 생성한 인코딩 결과만 저장한다. */
    public void changePassword(String encodedPassword) {
        this.password = Objects.requireNonNull(encodedPassword, "비밀번호 해시는 필수입니다.");
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        assertNotSuperAdmin();
        this.status = UserStatus.INACTIVE;
    }

    /** Redis 차단 전에 호출하며 유일한 슈퍼 관리자가 이용 불가 상태가 되는 것을 막는다. */
    public void assertNotSuperAdmin() {
        if (Role.SUPER_ADMIN.equals(roleCode)) throw new BusinessException(ErrorCode.ROLE_PROTECTED);
    }

    /** 일반 역할 변경으로 최고 권한을 만들거나 마지막 최고 권한을 없애지 않는다. */
    public void changeRole(String roleCode) {
        assertNotSuperAdmin();
        if (Role.SUPER_ADMIN.equals(roleCode)) throw new BusinessException(ErrorCode.ROLE_PROTECTED);
        assignRole(Objects.requireNonNull(roleCode));
    }

    /** 명시적인 초기 설정 서비스만 사용하며 활성 상태의 기존 계정에 최고 권한을 배정한다. */
    public void initializeSuperAdmin() {
        if (getDeletedAt() != null || status != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.ROLE_PROTECTED);
        assignRole(Role.SUPER_ADMIN);
    }

    /** 상위 클래스의 소프트 삭제 경로에서도 최고 관리자 보호 규칙을 적용한다. */
    @Override
    public void delete() {
        assertNotSuperAdmin();
        super.delete();
    }

    private void assignRole(String nextRole) {
        if (!Objects.equals(roleCode, nextRole)) {
            roleCode = nextRole;
            roleVersion = Math.incrementExact(roleVersion);
        }
    }
}
