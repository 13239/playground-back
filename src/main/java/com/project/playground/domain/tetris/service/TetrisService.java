package com.project.playground.domain.tetris.service;

import com.project.playground.domain.game.service.GameAvailabilityService;
import com.project.playground.domain.tetris.dto.*;
import com.project.playground.domain.tetris.repository.*;
import com.project.playground.global.exception.*;
import com.project.playground.global.security.session.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.util.stream.IntStream;

/** 브라우저에서 혼자 실행하고 종료 시 입력을 재검증한다. 랭킹에는 서버가 계산한 점수만 반영한다. */
@Service @RequiredArgsConstructor
public class TetrisService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TetrisRunRepository runs;
    private final TetrisStatsRepository stats;
    private final GameAvailabilityService availability;
    private final TetrisRateLimit limits;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TetrisResponse.Overview overview(Long userId, int page) {
        limits.acquire(userId, "overview");
        return readOverview(userId, page);
    }
    @Transactional
    public TetrisResponse.Start start(Session session, TetrisRequest.Start request) {
        limits.acquire(session.getId(), "start");
        availability.requireNewPlay("TETRIS", "SINGLE");
        runs.ensure(session.getId());
        var run = runs.lock(session.getId()).orElseThrow();
        String login = session.getSessionId().toString();
        // 응답 유실 뒤 같은 시작 ID는 시드를 유지한다. 시작 시에는 통계나 플레이 횟수를 갱신하지 않는다.
        if (request.getRequestId().equals(run.getRunId())) {
            if (run.isFinished() || !login.equals(run.getLoginSessionId()) || run.getRuleVersion() != TetrisReplay.VERSION)
                throw new BusinessException(ErrorCode.TETRIS_RUN_CHANGED);
        } else {
            long seed = Integer.toUnsignedLong(RANDOM.nextInt());
            run.begin(request.getRequestId(), login, seed == 0 ? 1 : seed, clock.instant(), TetrisReplay.VERSION);
        }
        return TetrisResponse.Start.builder().runId(run.getRunId()).seed(run.getSeed()).ruleVersion(TetrisReplay.VERSION).build();
    }
    @Transactional
    public TetrisResponse.Overview finish(Session session, TetrisRequest.Finish request) {
        limits.acquire(session.getId(), "finish");
        var run = runs.lock(session.getId()).orElseThrow(() -> new BusinessException(ErrorCode.TETRIS_RUN_CHANGED));
        if (!request.getRunId().equals(run.getRunId()) || !session.getSessionId().toString().equals(run.getLoginSessionId()))
            throw new BusinessException(ErrorCode.TETRIS_RUN_CHANGED);
        // 완료 응답 유실 뒤 재전송은 다시 집계·재생하지 않는다.
        if (run.isFinished()) return readOverview(session.getId(), 1);
        // 이전 규칙으로 시작한 판은 크기가 다른 새 규칙으로 검증하지 않는다.
        if (run.getRuleVersion() != TetrisReplay.VERSION) throw new BusinessException(ErrorCode.TETRIS_RUN_CHANGED);
        long elapsed = Duration.between(run.getStartedAt(), clock.instant()).toMillis();
        if (elapsed < 0 || elapsed > 7_200_000 || request.getTicks() > elapsed * 60 / 1000 + 180)
            throw new BusinessException(ErrorCode.TETRIS_REPLAY_INVALID);
        try {
            var replay = TetrisReplay.verify(run.getSeed(), request.getTicks(), request.getInputs());
            // 점수와 횟수 모두 서버가 재생해 게임 오버를 확인한 판만 저장한다. 중도 종료 제출은 거절한다.
            if (!replay.isOver()) throw new IllegalArgumentException("Replay must end with game over");
            stats.completed(session.getId(), replay.getScore());
        } catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.TETRIS_REPLAY_INVALID); }
        run.finish();
        return readOverview(session.getId(), 1);
    }
    private TetrisResponse.Overview readOverview(Long userId, int requestedPage) {
        var own = stats.own(userId).orElse(null);
        long best = own == null ? 0 : own.getBestScore();
        int pages = Math.max(1, (int)Math.ceilDiv(Math.min(100, stats.rankedCount()), 10));
        int page = Math.clamp(requestedPage, 1, pages), offset = (page - 1) * 10;
        var rows = stats.ranking(offset);
        var ranking = IntStream.range(0, rows.size()).mapToObj(index -> {
            var row = rows.get(index);
            return TetrisResponse.Ranking.builder().rank(offset + index + 1).nickname(row.getNickname())
                    .score(row.getScore()).self(row.getUserId().equals(userId)).build();
        }).toList();
        return TetrisResponse.Overview.builder().bestScore(best).rank(best > 0 ? stats.rank(userId, best) : null)
                .plays(own == null ? 0 : own.getPlays()).page(page).totalPages(pages).rankings(ranking).build();
    }
}
