package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.entity.GameFilter;
import lombok.Builder;
import lombok.Getter;

/** 관리 목록과 상세 모달에 숨김 게임까지 포함한 실제 연결 게임 수를 제공한다. */
@Getter @Builder
public class AdminGenreResponse {
    private final String code;
    private final String name;
    private final boolean enabled;
    private final int sortOrder;
    private final long version;
    private final long gameCount;

    public static AdminGenreResponse from(GameFilter genre, long gameCount) {
        return builder().code(genre.getCode()).name(genre.getName()).enabled(genre.isEnabled())
                .sortOrder(genre.getSortOrder()).version(genre.getVersion()).gameCount(gameCount).build();
    }
}
