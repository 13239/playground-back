package com.project.playground.domain.adminchat.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.util.List;

/** 채팅 서버의 페이지 번호·집계 계약을 그대로 전달한다. */
@Getter @Builder(toBuilder = true) @Jacksonized
public class AdminChatPage {
    private final List<AdminChatMessage> items;
    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;
}
