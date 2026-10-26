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
    private final boolean published;
    private final GameAvailability availability;
    private final String unavailableMessage;
    private final int sortOrder;
    private final Instant firstPublishedAt;
    private final long version;
    private final List<GameGenreResponse> genres;
    private final List<GameModeResponse> modes;
    // 공개 응답은 현재 공개 장르·지원 모드와 일치하는 선택만 전달한다.
    private final List<String> cardLabelCodes;
    private final List<GameFilterResponse> cardLabels;
    public static GameResponse from(Game game, List<GameGenreResponse> genres, List<GameModeResponse> modes, List<String> cardLabelCodes, List<GameFilterResponse> cardLabels) {
        return builder().code(game.getCode()).name(game.getName()).summary(game.getSummary()).description(game.getDescription())
                // 표지는 게임 코드에 연결하므로 별도의 관리용 이미지 키를 반환하지 않는다.
                .published(game.isPublished()).availability(game.getAvailability())
                .unavailableMessage(game.getUnavailableMessage()).sortOrder(game.getSortOrder()).firstPublishedAt(game.getFirstPublishedAt())
                .version(game.getVersion()).genres(genres).modes(modes).cardLabelCodes(cardLabelCodes).cardLabels(cardLabels).build();
    }
}
