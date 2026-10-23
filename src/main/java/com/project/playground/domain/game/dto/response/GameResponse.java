package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.entity.Game;
import com.project.playground.domain.game.enums.GameAvailability;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;

/** 카드와 상세 화면이 같은 카탈로그 계약을 사용하며 엔티티나 외부 실행 URL을 공개하지 않는다. */
@Getter @Builder
public class GameResponse {
    private final String code;
    private final String name;
    private final String summary;
    private final String description;
    private final String thumbnailKey;
    private final boolean published;
    private final GameAvailability availability;
    private final String unavailableMessage;
    private final int sortOrder;
    private final Instant firstPublishedAt;
    private final long version;
    private final List<GameGenreResponse> genres;
    private final List<GameModeResponse> modes;
    public static GameResponse from(Game game, List<GameGenreResponse> genres, List<GameModeResponse> modes) {
        return builder().code(game.getCode()).name(game.getName()).summary(game.getSummary()).description(game.getDescription())
                .thumbnailKey(game.getThumbnailKey()).published(game.isPublished()).availability(game.getAvailability())
                .unavailableMessage(game.getUnavailableMessage()).sortOrder(game.getSortOrder()).firstPublishedAt(game.getFirstPublishedAt())
                .version(game.getVersion()).genres(genres).modes(modes).build();
    }
}
