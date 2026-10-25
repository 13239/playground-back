package com.project.playground.domain.game.entity;

import com.project.playground.domain.game.enums.*;
import com.project.playground.domain.game.module.GameModeDefinition;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;

/** 지원 인원은 사람 수다. AI·관전자 수와 합산하거나 게임의 장르에 섞지 않는다. */
@Entity
@Table(name = "tbl_game_mode")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameMode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "game_id", nullable = false, updatable = false) private Long gameId;
    @Column(nullable = false, length = 64, updatable = false) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "graphics_type", nullable = false, length = 16, columnDefinition = "varchar(16)") private GraphicsType graphicsType;
    @Enumerated(EnumType.STRING) @Column(name = "connection_type", nullable = false, length = 16, columnDefinition = "varchar(16)") private ConnectionType connectionType;
    @Enumerated(EnumType.STRING) @Column(name = "interaction_type", nullable = false, length = 16, columnDefinition = "varchar(16)") private InteractionType interactionType;
    @Column(name = "min_human_players", nullable = false) private int minHumanPlayers;
    @Column(name = "max_human_players", nullable = false) private int maxHumanPlayers;
    @Column(name = "definition_version", nullable = false) private int definitionVersion;
    @Column(nullable = false) private boolean published;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, columnDefinition = "varchar(16)") private GameAvailability availability = GameAvailability.DISABLED;
    @Column(name = "unavailable_message", nullable = false, length = 300) private String unavailableMessage = "";
    @Version private Long version;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;

    @Builder
    public GameMode(Long gameId, GameModeDefinition definition, String name, int minHumanPlayers, int maxHumanPlayers) {
        this.gameId = gameId; this.code = definition.getCode();
        update(definition, name, minHumanPlayers, maxHumanPlayers);
    }
    /** 모드 순서는 배포된 구현 정의를 따르며 일반 수정으로 임의 순서를 저장하지 않는다. */
    public void update(GameModeDefinition definition, String name, int minHumanPlayers, int maxHumanPlayers) {
        this.name = name.strip(); this.minHumanPlayers = minHumanPlayers; this.maxHumanPlayers = maxHumanPlayers;
        this.graphicsType = definition.getGraphicsType(); this.connectionType = definition.getConnectionType();
        this.interactionType = definition.getInteractionType(); this.definitionVersion = definition.getVersion();
    }
    public void changeAvailability(boolean published, GameAvailability availability, String message) {
        this.published = published; this.availability = availability; this.unavailableMessage = message.strip();
    }
}
