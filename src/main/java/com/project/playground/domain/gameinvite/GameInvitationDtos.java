package com.project.playground.domain.gameinvite;

import com.project.playground.domain.social.SocialDtos;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;

/** 초대 공통 계약에는 공개 프로필과 게임·방 식별자만 싣고 이동 URL은 받지 않는다. */
public final class GameInvitationDtos {
    private GameInvitationDtos() {}
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Command {
        @NotBlank @Pattern(regexp="[a-z]{2,24}") private String game;
        @Size(max=64) private String roomId;
        @Size(max=36) private String targetId;
        @Size(max=36) private String requestId;
        @Size(max=36) private String invitationId;
    }
    @Getter @Builder
    public static class Invitation {
        private final String id;
        private final String game;
        private final String roomId;
        private final String roomTitle;
        private final SocialDtos.Person inviter;
        private final SocialDtos.Person target;
        private final Instant expiresAt;
    }
    @Getter @Builder
    public static class Decision { private final String state; private final String roomId; }
    @Getter @Builder
    public static class Room {
        private final String id;
        private final String title;
        private final String version;
    }
}
