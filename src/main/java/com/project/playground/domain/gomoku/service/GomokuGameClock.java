package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.repository.GomokuMatchRepository;
import com.project.playground.domain.gomoku.repository.GomokuRoomParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.PriorityQueue;

/** 브라우저가 멈춰도 서버가 패스·추첨 마감·접속 종료를 반영한다. 여러 서버의 실행은 방 잠금으로 합쳐진다.
 * 활성 판·페이지 종료의 마감만 메모리에 예약해 빈 대국·참가 테이블을 매초 조회하지 않는다. */
@Component @RequiredArgsConstructor @Slf4j
public class GomokuGameClock implements MessageListener, SubscriptionListener {
    private final GomokuMatchRepository matches;
    private final GomokuRoomParticipantRepository participants;
    private final GomokuWaitingRoomService waiting;
    private final Clock gomokuClock;
    // 다음 마감만 우선순위 큐에서 조회한다. 활성 방 수가 증가해도 매초 전체 방을 순회하지 않는다.
    private final Object scheduleLock = new Object();
    private final Map<Long, Due> deadlines = new HashMap<>();
    private final PriorityQueue<Due> dueQueue = new PriorityQueue<>((a, b) -> a.at.compareTo(b.at));
    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    public void started() {
        ready = true;
        recover();
    }

    /** 재시작·Redis 구독 복구 때만 미종료 판을 읽어 놓친 예약을 되살린다. 평상시에는 DB를 조회하지 않는다. */
    private synchronized void recover() {
        long afterId = 0;
        while (true) {
            var batch = matches.findActiveAfter(afterId, PageRequest.of(0, 100));
            batch.forEach(match -> schedule(match.getRoomId(), match.nextDeadline(GomokuGameService.DISCONNECT_SECONDS)));
            if (batch.size() < 100) break;
            afterId = batch.getLast().getId();
        }
        // 페이지 종료 예약도 영속 저장하므로 예약한 서버가 재시작돼도 퇴장이 누락되지 않는다.
        afterId = 0;
        while (true) {
            var batch = participants.findDepartingAfter(afterId, PageRequest.of(0, 100));
            batch.forEach(participant -> schedule(participant.getRoom().getId(), participant.getExitDeadline()));
            if (batch.size() < 100) return;
            afterId = batch.getLast().getId();
        }
    }

    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        // 최초 구독은 서버 준비 시 복구한다. 재구독은 장애 중 유실된 다른 서버의 예약을 복구한다.
        if (ready) recover();
    }

    @TransactionalEventListener
    public void committed(GomokuGameEvents.ClockChanged event) {
        schedule(event.getRoomId(), event.getDeadline());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String[] values = new String(message.getBody(), StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 2) return;
            long roomId = Long.parseLong(values[0]);
            if (roomId > 0) schedule(roomId, values[1].isEmpty() ? null : Instant.parse(values[1]));
        } catch (NumberFormatException | DateTimeException ignored) { /* 내부 토픽도 올바른 예약만 처리한다. */ }
    }

    private void schedule(Long roomId, Instant deadline) {
        // 지연·역순 알림은 마감을 늦추거나 새 재대국 예약을 없애지 못한다. 조기 확인 시 DB의 최신 마감으로 다시 예약한다.
        // Redis 알림은 역순 도착할 수 있다. 더 늦은 마감으로 기존 예약을 덮지 않고 DB에서 정확한 상태를 다시 확인한다.
        Instant dueAt = deadline == null ? gomokuClock.instant() : deadline;
        synchronized (scheduleLock) {
            Due previous = deadlines.get(roomId);
            if (previous != null && !dueAt.isBefore(previous.at)) return;
            Due next = new Due(roomId, dueAt);
            deadlines.put(roomId, next);
            dueQueue.offer(next);
            // 앞당겨진 이전 예약은 큐에 남을 수 있지만 꺼낼 때 객체 식별자로 무시한다.
        }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public void tick() {
        Instant now = gomokuClock.instant();
        var due = new ArrayList<Long>(100);
        synchronized (scheduleLock) {
            while (!dueQueue.isEmpty() && due.size() < 100 && !dueQueue.peek().at.isAfter(now)) {
                Due next = dueQueue.poll();
                if (deadlines.remove(next.roomId, next)) due.add(next.roomId);
            }
        }
        // SQL 트랜잭션 및 잠금은 메모리 스케줄러의 잠금 밖에서 실행한다.
        for (Long roomId : due) {
            try {
                Instant next = waiting.tick(roomId);
                if (next != null) schedule(roomId, next);
            } catch (RuntimeException exception) {
                // 한 방의 장애는 나머지 예약을 막지 않는다. 실패한 방만 재시도하며 빈 테이블을 조회하지 않는다.
                // 한 방의 장애는 나머지 마감을 막지 않는다. 실패한 방만 지수적 전역 조회 없이 재시도한다.
                schedule(roomId, now.plusSeconds(5));
                log.warn("오목 대국 시각을 반영하지 못했습니다. roomId={}", roomId, exception);
            }
        }
    }

    private static final class Due {
        private final Long roomId;
        private final Instant at;
        private Due(Long roomId, Instant at) { this.roomId = roomId; this.at = at; }
    }
}
