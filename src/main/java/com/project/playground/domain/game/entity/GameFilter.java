package com.project.playground.domain.game.entity;

import jakarta.persistence.*;
import com.project.playground.domain.game.enums.GameFilterType;
import com.project.playground.domain.game.enums.GraphicsType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;

/** 장르는 행 데이터로 추가하고 삭제 대신 비활성화해 과거 게임 연결을 보존한다. */
@Entity
@Table(name = "tbl_game_filter")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameFilter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64, updatable = false) private String code;
    @Column(nullable = false, length = 50) private String name;
    // 유형·코드는 생성 후 고정해 이미 선택한 라벨의 식별자를 바꾸지 않는다.
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false, columnDefinition = "varchar(16)")
    private GameFilterType type;
    @Column(name = "label_code", nullable = false, length = 80, updatable = false) private String labelCode;
    @Column(nullable = false, length = 32) private String icon;
    @Column(name = "min_players") private Integer minPlayers;
    @Column(name = "max_players") private Integer maxPlayers;
    @Enumerated(EnumType.STRING) @Column(name = "graphics_type", length = 16, columnDefinition = "varchar(16)")
    private GraphicsType graphicsType;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Version private Long version;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;

    @Builder
    public GameFilter(String code, String name, boolean enabled, int sortOrder, GameFilterType type,
                      String icon, Integer minPlayers, Integer maxPlayers, GraphicsType graphicsType) {
        this.code = code;
        this.type = type == null ? GameFilterType.GENRE : type;
        this.labelCode = this.type.name() + ":" + code;
        configure(icon == null ? "gamepad" : icon, minPlayers, maxPlayers, graphicsType);
        this.sortOrder = sortOrder;
        update(name, enabled);
    }
    /** 장르의 정보 편집과 그리드 순서 저장을 분리해 이전 상세 화면이 새 순서를 덮어쓰지 않게 한다. */
    public void update(String name, boolean enabled) {
        this.name = name.strip(); this.enabled = enabled;
    }
    /** 검증된 표시·지원 조건만 변경하고 노출 순서는 별도 순서 API로 관리한다. */
    public void configure(String icon, Integer minPlayers, Integer maxPlayers, GraphicsType graphicsType) {
        this.icon = icon; this.minPlayers = minPlayers; this.maxPlayers = maxPlayers; this.graphicsType = graphicsType;
    }
    /** 실제 지원 범위와 겹치는 인원·그래픽만 분류할 수 있으며 선택 자체는 별도 검증한다. */
    public boolean supportsMode(GraphicsType graphics,int min,int max) {
        return switch(type) {
            case GENRE -> true;
            case PLAYERS -> minPlayers<=max && maxPlayers>=min;
            case GRAPHICS -> graphicsType==graphics;
        };
    }
}
