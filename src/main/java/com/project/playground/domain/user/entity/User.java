package com.project.playground.domain.user.entity;

import com.project.playground.domain.user.enums.Gender;
import com.project.playground.domain.user.enums.ProfileAvatar;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.global.audit.TimeEntity;
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

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }
}
