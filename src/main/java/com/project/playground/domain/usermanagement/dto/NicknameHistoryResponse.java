package com.project.playground.domain.usermanagement.dto;

import com.project.playground.domain.user.entity.UserNicknameHistory;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/** 변경자는 현재 정책상 본인뿐이며 저장된 스냅샷을 최신순 번호 페이지로 표시한다. */
@Getter @Builder
public class NicknameHistoryResponse {
    private final List<Item> items;
    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;
    @Getter @Builder
    public static class Item {
        private final Long id;
        private final String previousNickname;
        private final String newNickname;
        private final LocalDateTime changedAt;
        private final String changedBy;
        public static Item from(UserNicknameHistory h) {
            return Item.builder().id(h.getId()).previousNickname(h.getPreviousNickname()).newNickname(h.getNewNickname())
                    .changedAt(h.getChangedAt()).changedBy("SELF").build();
        }
    }
}
