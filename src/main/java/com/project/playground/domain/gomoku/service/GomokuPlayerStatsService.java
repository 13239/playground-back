package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.entity.GomokuMatch;
import com.project.playground.domain.gomoku.entity.GomokuPlayerStats;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.domain.gomoku.repository.GomokuPlayerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 온라인 대국의 최종 서버 판정만 집계하며 대국 종료와 같은 MySQL 트랜잭션을 사용한다. */
@Service
@RequiredArgsConstructor
public class GomokuPlayerStatsService {
    private final GomokuPlayerStatsRepository repository;

    /** 이미 종료된 판·중복 요청·기능 이전의 판정은 전적을 재기록하지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFinished(GomokuMatch match) {
        if (match.getPhase() != GomokuMatch.Phase.FINISHED || match.isStatsRecorded()) return;

        switch (match.getFinishReason()) {
            case FIVE, RESIGN, DISCONNECT -> {
                if (match.getWinnerSeat() != 1 && match.getWinnerSeat() != 2) {
                    throw new IllegalStateException("승패 대국에 유효한 승자가 없습니다.");
                }
                boolean firstWon = match.getWinnerSeat() == 1;
                repository.increment(match.getUserOneId(), firstWon ? 1 : 0, firstWon ? 0 : 1, 0);
                repository.increment(match.getUserTwoId(), firstWon ? 0 : 1, firstWon ? 1 : 0, 0);
            }
            case BOARD_FULL -> {
                if (match.getWinnerSeat() != 0) throw new IllegalStateException("무승부에 승자가 지정되었습니다.");
                repository.increment(match.getUserOneId(), 0, 0, 1);
                repository.increment(match.getUserTwoId(), 0, 0, 1);
            }
            // 양쪽 동시 접속 종료/장시간 서버 중단은 승패·무승부 어느 쪽에도 포함하지 않는다.
            case INTERRUPTED -> { }
        }
        // 방 행 잠금 아래의 한 트랜잭션에서 갱신하므로 재시도와 타이머 중복 완료에도 한 번만 반영된다.
        match.markStatsRecorded();
    }

    /** 최대 두 참가자의 누적 전적만 PK로 읽는다. 한 건도 없으면 DTO에서 모두 0으로 표시한다. */
    public Map<Long, GomokuPlayerStats> forMembers(List<GomokuRoomParticipant> members) {
        var ids = members.stream().map(member -> member.getUser().getId()).toList();
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(GomokuPlayerStats::getUserId, Function.identity()));
    }
}
