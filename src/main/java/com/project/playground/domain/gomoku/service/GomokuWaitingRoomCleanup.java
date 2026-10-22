package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.repository.GomokuRoomParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 강제 종료로 남은 대기실·대국 참가를 작은 배치로 정리하며 다른 인스턴스·입장 요청과 방 행 잠금을 공유한다. */
@Slf4j @Component @RequiredArgsConstructor
public class GomokuWaitingRoomCleanup {
    private final GomokuRoomParticipantRepository participants;
    private final GomokuWaitingRoomService waiting;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void clean() {
        for (Long roomId : participants.findExpiredRoomIds(Instant.now().minusSeconds(90), Instant.now().minusSeconds(180),
                com.project.playground.domain.gomoku.enums.RoomStatus.PLAYING, PageRequest.of(0, 100))) {
            try { waiting.expire(roomId); }
            catch (RuntimeException exception) {
                // 한 방의 잠금 경합·DB 장애가 나머지 정리나 다음 주기를 중단하지 않게 한다.
                log.warn("오목 대기실 만료 정리를 완료하지 못했습니다. roomId={}", roomId);
            }
        }
    }
}
