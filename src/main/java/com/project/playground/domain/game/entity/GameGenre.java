package com.project.playground.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;

/** 장르는 행 데이터로 추가하고 삭제 대신 비활성화해 과거 게임 연결을 보존한다. */
@Entity
@Table(name = "tbl_game_genre")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameGenre {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64, updatable = false) private String code;
    @Column(nullable = false, length = 50) private String name;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Version private Long version;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;

    @Builder
    public GameGenre(String code, String name, boolean enabled, int sortOrder) {
        this.code = code;
        this.sortOrder = sortOrder;
        update(name, enabled);
    }
    /** 장르의 정보 편집과 그리드 순서 저장을 분리해 이전 상세 화면이 새 순서를 덮어쓰지 않게 한다. */
    public void update(String name, boolean enabled) {
        this.name = name.strip(); this.enabled = enabled;
    }
}
