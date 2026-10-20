package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 검색 조건도 브라우저 서명 본문에 포함한다. 정렬은 최신 방 순, 페이지 크기는 서버에서 고정한다. */
@Getter
@Builder
@Jacksonized
public class SearchRoomsRequest {
    @Size(max = 30)
    private final String query;
    private final boolean publicOnly;
    private final boolean waitingOnly;
    @Min(0)
    @Max(1000)
    private final int page;
}
