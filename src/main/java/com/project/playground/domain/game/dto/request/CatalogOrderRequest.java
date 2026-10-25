package com.project.playground.domain.game.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.util.List;

/** 전체 목록을 업로드하지 않고 최대 100개의 이동을 요청 순서대로 한 트랜잭션에 반영한다. */
@Getter @Builder @Jacksonized
public class CatalogOrderRequest {
    @NotNull @PositiveOrZero
    private final Long expectedVersion;

    @NotEmpty @Size(max = 100)
    private final List<@NotNull @Valid CatalogOrderMoveRequest> moves;
}
