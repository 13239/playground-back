-- 로그인·회원가입 등 사용자 활동을 하나의 테이블에 누적한다. 새 활동은 컬럼 추가 없이 activity_type 값만 늘린다.
-- 회원 행 잠금과 경합하지 않도록 tbl_user 외래키를 두지 않으며, 로그는 추가만 하고 수정하지 않는다.
CREATE TABLE tbl_activity_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activity_type VARCHAR(40) NOT NULL COMMENT '활동 종류 (LOGIN, SIGN_UP ...)',
    result VARCHAR(10) NOT NULL COMMENT '처리 결과 (SUCCESS, FAILURE)',
    failure_code VARCHAR(50) NULL COMMENT '실패 시 ErrorCode 이름',
    user_id BIGINT NULL COMMENT '활동 회원. 미등록 아이디의 로그인 실패·가입 실패는 NULL',
    client_ip VARCHAR(45) NULL COMMENT '요청 IP. 요청 밖의 활동이나 IP를 전달받지 못한 요청은 NULL',
    user_agent VARCHAR(255) NULL COMMENT 'BFF가 전달한 브라우저 User-Agent',
    detail JSON NULL COMMENT '활동별 부가 정보. 허용한 키만 저장',
    occurred_at DATETIME(6) NOT NULL COMMENT '활동 발생 시각',
    INDEX idx_activity_log_occurred (occurred_at),
    INDEX idx_activity_log_user (user_id, occurred_at),
    INDEX idx_activity_log_type (activity_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 활동 로그';
