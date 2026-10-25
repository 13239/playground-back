package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.enums.GameAvailability;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 목록에는 긴 설명이나 이미지 설정을 보내지 않고 상세 모달에서 필요한 항목만 다시 조회한다. */
@Getter @Builder
public class AdminGameSummaryResponse {
    private final String code;
    private final String name;
    private final int sortOrder;
    private final long version;
    private final List<GameGenreResponse> genres;
    private final List<GameModeResponse> modes;
    private final boolean published;
    private final GameAvailability availability;

    public static AdminGameSummaryResponse from(GameResponse game) {
        return builder().code(game.getCode()).name(game.getName()).sortOrder(game.getSortOrder()).version(game.getVersion())
                .genres(game.getGenres()).modes(game.getModes()).published(game.isPublished()).availability(game.getAvailability()).build();
    }
}
