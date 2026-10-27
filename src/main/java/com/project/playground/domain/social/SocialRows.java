package com.project.playground.domain.social;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
/** 저장소 내부 행 모델이다. 공개 응답에는 SocialDtos로 필요한 값만 복사한다. */
final class SocialRows {
    private SocialRows() {}
    @Getter @Builder static class Account { private long id; private String publicId; private String nickname; private String avatarId; }
    @Getter @Builder static class Friend { private long first; private long second; private long requester; private String id; private String state; private Instant requestedAt; private Instant resolvedAt; }
    @Getter @Builder static class Room { private String id; private String kind; private String name; private long owner; private long until; private boolean closed; }
    @Getter @Builder static class Member { private long userId; private String membershipId; private long since; private boolean active; private boolean muted; private Account person; }
    @Getter @Builder static class Invite { private String id; private String roomId; private long inviter; private long target; private String state; private Instant expiresAt; }
}

