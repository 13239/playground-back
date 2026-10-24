-- 새 기능 적용 이후 완료된 온라인 대국만 집계한다. 종료된 과거 대국은 일괄 통계 생성하지 않는다.
CREATE TABLE tbl_gomoku_player_stats (
    user_id BIGINT NOT NULL PRIMARY KEY COMMENT '회원별 오목 온라인 대국 전적',
    wins BIGINT NOT NULL DEFAULT 0,
    losses BIGINT NOT NULL DEFAULT 0,
    draws BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_gomoku_stats_user FOREIGN KEY (user_id) REFERENCES tbl_user(id),
    CONSTRAINT ck_gomoku_stats_nonnegative CHECK (wins >= 0 AND losses >= 0 AND draws >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='신규 온라인 오목 확정 승패무 누적 전적';

-- 대국별 기록 여부도 트랜잭션으로 저장해 재요청이나 예약 중복 처리에서 중복 집계를 방지한다.
ALTER TABLE tbl_gomoku_match
    ADD COLUMN stats_recorded BOOLEAN NOT NULL DEFAULT FALSE COMMENT '온라인 전적 집계 완료 여부';

-- 마이그레이션 이전에 종료된 대국은 통계에 포함하지 않고 집계 대상에서 명시적으로 제외한다.
UPDATE tbl_gomoku_match SET stats_recorded = TRUE WHERE phase = 'FINISHED';
