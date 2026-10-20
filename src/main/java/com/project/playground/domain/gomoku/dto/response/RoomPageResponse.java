package com.project.playground.domain.gomoku.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 고정 크기의 목록과 페이지 정보를 명시적으로 제공하고 열린 본인 방은 별도로 안내한다. */
@Getter
@Builder
public class RoomPageResponse {
    private final List<RoomSummaryResponse> rooms;
    private final int page;
    private final int pageSize;
    private final long totalElements;
    private final int totalPages;
    private final HostedRoomResponse hostedRoom;
}
