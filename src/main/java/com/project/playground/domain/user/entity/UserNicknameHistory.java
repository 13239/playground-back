package com.project.playground.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;

/** 실제 본인 변경의 스냅샷을 추가만 한다. 회원의 현재 닉네임을 조인해 과거 값을 바꾸지 않는다. */
@Entity @Immutable @Table(name = "tbl_user_nickname_history")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNicknameHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "actor_user_id", nullable = false) private Long actorUserId;
    @Column(name = "previous_nickname", nullable = false, length = 50) private String previousNickname;
    @Column(name = "new_nickname", nullable = false, length = 50) private String newNickname;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;

    @Builder
    public UserNicknameHistory(Long userId, Long actorUserId, String previousNickname, String newNickname, LocalDateTime changedAt) {
        this.userId = userId; this.actorUserId = actorUserId; this.previousNickname = previousNickname;
        this.newNickname = newNickname; this.changedAt = changedAt;
    }
}
