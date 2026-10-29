package com.project.playground.domain.social;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** 친구/요청/알림의 공개 계약이다. 내부 회원 ID·로그인 정보는 직렬화하지 않는다. */
public final class SocialDtos {
    private SocialDtos() {}
    @Getter @Builder @Jacksonized
    public static class Command {
        private final String targetId;
        private final String requestId;
        private final String roomId;
        private final String invitationId;
        private final String notificationId;
        private final String name;
        private final List<String> userIds;
        private final String beforeId;
        private final Boolean muted;
        private final Integer page;
        private final Integer friendPage;
        private final Integer requestPage;
        private final Integer sentPage;
        private final Integer roomPage;
        private final Integer notificationPage;
        private final String search;
    }
    @Getter @Builder(toBuilder = true) @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Person {
        private final String publicId;
        private final String nickname;
        private final String avatarId;
        private final String relation;
        private final String requestId;
        private final Boolean online;
        private final Boolean blocked;
    }
    @Getter @Builder
    public static class Request {
        private final String id;
        private final Person person;
        private final Instant createdAt;
    }
    @Getter @Builder
    public static class Member {
        private final Person person;
        private final boolean owner;
        private final boolean self;
    }
    @Getter @Builder
    public static class Room {
        private final String id;
        private final String kind;
        private final String name;
        private final boolean owner;
        private final boolean writable;
        private final boolean muted;
        private final String membershipId;
        private final List<Member> members;
        private final List<PendingInvitation> invitations;
    }
    @Getter @Builder
    public static class PendingInvitation {
        private final String id;
        private final Person person;
        private final Instant expiresAt;
        private final boolean cancellable;
    }
    @Getter @Builder
    public static class RequestLocation {
        private final boolean active;
        private final int page;
    }
    @Getter @Builder
    public static class Invitation {
        private final String id;
        private final String roomId;
        private final String roomName;
        private final Person inviter;
        private final Instant expiresAt;
    }
    @Getter @Builder
    public static class Notification {
        private final String id;
        private final String kind;
        private final String targetId;
        private final Person actor;
        private final Instant createdAt;
        private final boolean read;
    }
    @Getter @Builder
    public static class Page<T> {
        private final List<T> items;
        private final long total;
        private final int page;
        private final int pageSize;
        public static <T> Page<T> of(List<T> items, long total, int page) {
            return Page.<T>builder().items(items).total(total).page(page).pageSize(12).build();
        }
    }
    @Getter @Builder
    public static class Snapshot {
        private final Person self;
        private final Page<Person> friends;
        private final Page<Request> received;
        private final Page<Request> sent;
        private final Page<Room> rooms;
        private final Page<Notification> notifications;
        private final List<Invitation> invitations;
        private final long unreadNotifications;
        private final String notificationBoundary;
        // 짧은 오목 초대는 일반 알림 읽음과 분리하고 서버 시각을 기준으로 남은 시간을 계산한다.
        private final List<com.project.playground.domain.gomoku.dto.response.GomokuInvitationResponse> gomokuInvitations;
        // 게임 초대의 기한과 공개 프로필만 전달하며 정답·참여코드는 포함하지 않는다.
        private final List<com.project.playground.domain.memory.MemoryDtos.Invitation> memoryInvitations;
        // 게임별 입장 조건은 어댑터가 검증하며 화면은 공통 초대 카드로 표시한다.
        private final List<com.project.playground.domain.gameinvite.GameInvitationDtos.Invitation> gameInvitations;
        private final Instant serverTime;
    }
}

