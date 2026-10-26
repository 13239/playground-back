package com.project.playground.domain.game.entity;

import com.project.playground.domain.game.enums.GameAvailability;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.*;

/** 공통 운영 정보만 보관하며 게임 규칙·렌더러·실행 URL은 저장하지 않는다. */
@Entity
@Table(name = "tbl_game")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64, updatable = false) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 160) private String summary;
    @Column(nullable = false, length = 2000) private String description;
    @Column(nullable = false) private boolean published;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, columnDefinition = "varchar(16)")
    private GameAvailability availability = GameAvailability.DISABLED;
    @Column(name = "unavailable_message", nullable = false, length = 300) private String unavailableMessage = "";
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "first_published_at") private Instant firstPublishedAt;
    @Version private Long version;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @Getter(AccessLevel.NONE)
    @ManyToMany
    @JoinTable(name = "tbl_game_genre_mapping", joinColumns = @JoinColumn(name = "game_id"), inverseJoinColumns = @JoinColumn(name = "genre_id"))
    @BatchSize(size = 100)
    private Set<GameFilter> genres = new HashSet<>();

    // 페이지 안의 라벨을 일괄 조회해 카드 수에 비례하는 개별 SELECT를 만들지 않는다.
    @Getter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "tbl_game_card_label", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "label_code", nullable = false, length = 80)
    @BatchSize(size = 100)
    private Set<String> cardLabelCodes = new HashSet<>();

    @Builder
    public Game(String code, String name, String summary, String description, int sortOrder, Set<GameFilter> genres, Set<String> cardLabelCodes) {
        this.code = code;
        this.sortOrder = sortOrder;
        update(name, summary, description, genres, cardLabelCodes == null ? Set.of() : cardLabelCodes);
    }
    public Set<GameFilter> getGenres() { return Collections.unmodifiableSet(genres); }
    public Set<String> getCardLabelCodes() { return Collections.unmodifiableSet(cardLabelCodes); }
    /** 일반 정보 저장은 그리드에서 저장한 전체 노출 순서를 변경하지 않는다. */
    public void update(String name, String summary, String description, Set<GameFilter> genres, Set<String> cardLabelCodes) {
        this.name = name.strip(); this.summary = summary.strip(); this.description = description.strip();
        // 표지는 프론트의 게임 코드별 구현에서 결정하고 관리 데이터에는 이미지 선택값을 저장하지 않는다.
        this.genres.clear(); this.genres.addAll(genres);
        // 빈 선택은 라벨을 전부 숨긴다는 뜻이며 장르·모드에서 기본 라벨을 다시 생성하지 않는다.
        this.cardLabelCodes.clear(); this.cardLabelCodes.addAll(cardLabelCodes);
    }
    /** 공개·이용 상태 편집은 메타데이터 편집과 별도 권한으로 호출한다. */
    public void changeAvailability(boolean published, GameAvailability availability, String message) {
        if (published && !this.published && firstPublishedAt == null) firstPublishedAt = Instant.now();
        this.published = published; this.availability = availability; this.unavailableMessage = message.strip();
    }
}
