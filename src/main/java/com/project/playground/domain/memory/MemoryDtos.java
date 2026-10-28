package com.project.playground.domain.memory;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;
import java.util.List;
import com.project.playground.domain.social.SocialDtos.Person;

/** 요청에 점수·경과 시간·정답을 받지 않는다. 응답도 엔티티 대신 공개 필드만 구성한다. */
public final class MemoryDtos {
    private MemoryDtos() { }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Command {
        @Min(1) private Long roomId;
        @Min(1) @Max(10000) private Integer page;
        @Min(1) @Max(10000) private Integer roomPage;
        @Pattern(regexp = "[0-9a-f-]{36}") private String tabId;
        @Pattern(regexp = "[0-9a-f-]{36}") private String requestId;
        @Pattern(regexp = "[0-9a-f-]{36}") private String matchId;
        @Min(0) private Long revision;
        @Size(max = 30) private String title;
        @Size(max = 64) private String password;
        @Pattern(regexp = "[A-Z0-9]{6}") private String code;
        private Boolean privateRoom;
        private Boolean ready;
        @Min(0) @Max(29) private Integer position;
        @Pattern(regexp = "[0-9a-f-]{36}") private String targetId;
        @Pattern(regexp = "[0-9a-f-]{36}") private String invitationId;
    }
    @Getter @Builder public static class Player {
        private final String nickname;
        private final String avatarId;
        private final boolean self;
        private final boolean ready;
        private final boolean rematch;
        private final long wins;
        private final long losses;
    }
    @Getter @Builder public static class State {
        private final Long roomId;
        private final String title;
        private final String code;
        private final boolean solo;
        private final boolean privateRoom;
        private final boolean closed;
        private final long revision;
        private final String matchId;
        private final String phase;
        private final Instant serverTime;
        private final Instant startedAt;
        private final Instant deadline;
        private final Instant finishedAt;
        private final int seat;
        private final int turn;
        private final int scoreOne;
        private final int scoreTwo;
        private final int attempts;
        private final int winner;
        private final String finishReason;
        private final List<Integer> cards;
        private final String owners;
        private final Player one;
        private final Player two;
    }
    @Getter @Builder public static class RoomItem {
        private final Long roomId;
        private final String title;
        private final boolean privateRoom;
        private final int players;
    }
    @Getter @Builder public static class Ranking {
        private final long rank;
        private final String nickname;
        private final long millis;
        private final int attempts;
        private final Instant achievedAt;
        private final boolean self;
    }
    @Getter @Builder public static class Overview {
        private final List<Ranking> rankings;
        private final List<RoomItem> rooms;
        private final int page;
        private final int totalPages;
        private final int roomPage;
        private final int roomPages;
        private final Long currentRoomId;
        private final Long bestMillis;
        private final Long rank;
    }
    @Getter @Builder public static class Invitation {
        private final String id;
        private final Long roomId;
        private final String roomTitle;
        private final Person inviter;
        private final Person target;
        private final Instant expiresAt;
    }
    @Getter @Builder public static class Decision {
        private final String state;
        private final Long roomId;
    }
}
