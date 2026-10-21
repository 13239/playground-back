package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/** MySQL을 참가 상태의 원본으로 유지하고 커밋된 권한만 짧은 Redis 리스로 chat에 전달한다. */
@Slf4j @Component @RequiredArgsConstructor
public class RoomChatAccessPublisher {
    private static final RedisScript<Long> PUBLISH = RedisScript.of(new ClassPathResource("redis/gomoku/publish-access.lua"), Long.class);
    private static final RedisScript<Long> TIME = RedisScript.of(new ClassPathResource("redis/gomoku/time.lua"), Long.class);
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher events;

    public long nowMicros() {
        try {
            Long value = redis.execute(TIME, List.of());
            if (value == null || value <= 0) throw new BusinessException(ErrorCode.ROOM_SERVICE_UNAVAILABLE);
            return value;
        } catch (DataAccessException exception) { throw new BusinessException(ErrorCode.ROOM_SERVICE_UNAVAILABLE); }
    }

    public void changed(GomokuRoom room, List<GomokuRoomParticipant> members) {
        var arguments = new ArrayList<String>();
        arguments.add(Long.toString(room.getRevision()));
        arguments.add("25000");
        for (var member : members) {
            arguments.add("user:" + member.getUser().getId());
            arguments.add(member.getJoinedAtMicros() + "|" + member.getMembershipId());
        }
        events.publishEvent(AccessChanged.builder().roomId(room.getId()).arguments(List.copyOf(arguments)).build());
    }

    /** 실패한 SQL 트랜잭션은 권한을 만들지 않는다. 장애 중에는 리스가 만료되어 방 채팅이 닫힌다. */
    @TransactionalEventListener
    public void committed(AccessChanged event) {
        try {
            redis.execute(PUBLISH, List.of("gomoku:room:access:" + event.getRoomId(),
                    "gomoku:room:access-version:" + event.getRoomId()), event.getArguments().toArray());
        } catch (DataAccessException exception) {
            log.warn("오목 방 채팅 권한을 동기화하지 못했습니다. roomId={}", event.getRoomId());
            // 기존 리스도 즉시 제거를 시도하며 Redis 전체 장애면 최대 25초 뒤 자동 만료된다.
            try { redis.delete("gomoku:room:access:" + event.getRoomId()); } catch (DataAccessException ignored) { }
        }
    }

    @Getter @Builder
    public static class AccessChanged {
        private final Long roomId;
        private final List<String> arguments;
    }
}
