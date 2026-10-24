-- 회원별 방문 이력 없이 분별 최대 동시 접속만 보관한다. 모든 시각은 이 테이블에서 UTC다.
CREATE TABLE tbl_admin_connection_sample (
    sampled_at DATETIME NOT NULL PRIMARY KEY COMMENT 'Redis 시계의 UTC 분 시작',
    peak_count BIGINT NOT NULL COMMENT '60초 접속 확인 회원 수의 분별 최대',
    observed_at DATETIME(3) NOT NULL COMMENT '마지막 실제 관측 시각 UTC',
    CONSTRAINT chk_admin_connection_peak_nonnegative CHECK (peak_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리자 접속 추이 집계';

-- 기존 회원 생성 시각은 변경하지 않는다. KST 날짜 경계를 저장 시간대로 변환해 범위 인덱스로 읽는다.
ALTER TABLE tbl_user ADD INDEX idx_user_created_at (created_at);
