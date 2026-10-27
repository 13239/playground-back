package com.project.playground.domain.user.service;

import com.project.playground.domain.user.dto.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/** 채팅은 메시지 원문을 수정하지 않고 이 최신 프로필 투영으로 작성자를 표시한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatProfilePublisher {
    private static final String PREFIX = "chat:profiles:{profiles}:user:";
    private static final String REVISION = "chat:profiles:{profiles}:revision";
    private static final String CHANNEL = "chat:profiles:changed:v1";
    private static final RedisScript<Long> PUBLISH = RedisScript.of(
            new ClassPathResource("redis/chat/publish-profile.lua"), Long.class);
    private final StringRedisTemplate redis;

    /** 사용자 행 잠금 안에서 순서를 예약하고 DB 커밋에 성공한 프로필만 알린다. */
    public void afterCommit(Long userId, UserProfileResponse profile) {
        long revision = nextRevision();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try { publish(userId, profile, revision); }
                catch (RuntimeException exception) {
                    // DB 저장 성공을 실패로 바꾸지 않는다. 다음 티켓 발급이 최신 DB 프로필을 다시 동기화한다.
                    log.warn("채팅 프로필 알림을 즉시 반영하지 못했습니다. 다음 접속에서 다시 동기화합니다.");
                }
            }
        });
    }

    /** 공유 행 잠금으로 읽은 최신 프로필을 티켓 발급 전에 보충해 알림 유실·재접속도 복구한다. */
    public void synchronize(Long userId, UserProfileResponse profile) {
        publish(userId, profile, nextRevision());
    }

    private long nextRevision() {
        Long revision = redis.opsForValue().increment(REVISION);
        if (revision == null || revision < 1) throw new IllegalStateException("프로필 변경 순서를 확인하지 못했습니다.");
        return revision;
    }

    private void publish(Long userId, UserProfileResponse profile, long revision) {
        Long result = redis.execute(PUBLISH, List.of(PREFIX + userId),
                UUID.randomUUID().toString(), profile.getNickname(), profile.getAvatarId().name(), Long.toString(revision), CHANNEL,
                profile.getPublicUserId());
        if (result == null) throw new IllegalStateException("채팅 프로필 동기화를 확인하지 못했습니다.");
    }
}
