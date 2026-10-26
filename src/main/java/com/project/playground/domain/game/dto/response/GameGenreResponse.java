package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.entity.GameFilter;
import lombok.Builder;
import lombok.Getter;

/** 장르 목록은 회원 수나 고정된 장르 enum 없이 전달한다. */
@Getter @Builder
public class GameGenreResponse {
    private final String code;
    private final String name;
    private final boolean enabled;
    private final int sortOrder;
    private final long version;
    public static GameGenreResponse from(GameFilter genre) {
        return builder().code(genre.getCode()).name(genre.getName()).enabled(genre.isEnabled())
                .sortOrder(genre.getSortOrder()).version(genre.getVersion()).build();
    }
}
