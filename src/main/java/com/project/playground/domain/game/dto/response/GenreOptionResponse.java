package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.entity.GameGenre;
import lombok.Builder;
import lombok.Getter;

/** 검색 콤보박스에는 장르의 코드·표시 이름·사용 여부만 전달한다. */
@Getter @Builder
public class GenreOptionResponse {
    private final String code;
    private final String name;
    private final boolean enabled;

    public static GenreOptionResponse from(GameGenre genre) {
        return builder().code(genre.getCode()).name(genre.getName()).enabled(genre.isEnabled()).build();
    }
}
