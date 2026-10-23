-- 관리자 권한은 Spring Security의 authority 대신 DB 역할과 기능 매핑으로 판단한다.
-- 게임 등 후속 기능은 권한 코드만 먼저 등록하며 이 SQL에서 업무 테이블이나 기존 데이터를 삭제하지 않는다.
CREATE TABLE tbl_role (
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY COMMENT '변경하지 않는 역할 코드',
    name VARCHAR(50) NOT NULL COMMENT '역할 표시 이름',
    description VARCHAR(255) NULL COMMENT '역할 설명',
    system_role BOOLEAN NOT NULL DEFAULT FALSE COMMENT '기본 역할 삭제 금지 여부',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '역할 편집 충돌 감지',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리 역할';

CREATE TABLE tbl_permission (
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY COMMENT '코드에 정의된 기능 권한',
    name VARCHAR(100) NOT NULL,
    category VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    super_admin_only BOOLEAN NOT NULL DEFAULT FALSE COMMENT '다른 역할로 위임할 수 없는 기능'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='기능 권한 사전';

CREATE TABLE tbl_role_permission (
    role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    permission_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (role_code, permission_code),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_code) REFERENCES tbl_role (code),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_code) REFERENCES tbl_permission (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='역할별 기능 허용';

INSERT INTO tbl_role (code, name, description, system_role, created_at) VALUES
('SUPER_ADMIN', '슈퍼 관리자', '단일 최고 관리자. 모든 관리 기능을 사용할 수 있습니다.', TRUE, CURRENT_TIMESTAMP(6)),
('ADMIN', '관리자', '사용자와 운영 기능을 관리합니다.', TRUE, CURRENT_TIMESTAMP(6)),
('MANAGER', '운영 매니저', '사용자와 채팅 내역을 확인합니다.', TRUE, CURRENT_TIMESTAMP(6)),
('USER', '일반 사용자', '기본 회원 역할입니다.', TRUE, CURRENT_TIMESTAMP(6));

INSERT INTO tbl_permission (code, name, category, super_admin_only) VALUES
('ADMIN_ACCESS', '관리자 페이지 접근', 'ACCESS', FALSE),
('DASHBOARD_READ', '운영 대시보드 조회', 'DASHBOARD', FALSE),
('USER_READ', '사용자 조회', 'USER', FALSE),
('USER_STATUS_UPDATE', '계정 제한·해제', 'USER', FALSE),
('USER_SESSION_REVOKE', '사용자 세션 종료', 'USER', FALSE),
('USER_SENSITIVE_READ', '사용자 개인정보 열람', 'USER', FALSE),
('USER_ROLE_UPDATE', '회원 역할 변경', 'AUTHORIZATION', TRUE),
('ROLE_READ', '역할·권한 조회', 'AUTHORIZATION', TRUE),
('ROLE_WRITE', '역할 추가·수정·삭제', 'AUTHORIZATION', TRUE),
('ROLE_PERMISSION_UPDATE', '역할별 기능 권한 변경', 'AUTHORIZATION', TRUE),
('ACCESS_POLICY_READ', '접속 제한 설정 조회', 'OPERATIONS', FALSE),
('ACCESS_POLICY_UPDATE', '접속 제한·안내 문구 변경', 'OPERATIONS', FALSE),
('ACCESS_EXEMPTION_UPDATE', '접속 허용 역할 지정', 'OPERATIONS', TRUE),
('CHAT_HISTORY_READ', '채팅 내역 조회', 'CHAT', FALSE),
('ACTIVITY_LOG_READ', '사용자 활동 로그 조회', 'LOG', FALSE),
('ADMIN_AUDIT_READ', '관리자 작업 기록 조회', 'LOG', FALSE),
('GAME_READ', '게임 목록·설정 조회', 'GAME', FALSE),
('GAME_WRITE', '게임 정보·장르·모드 변경', 'GAME', FALSE),
('GAME_AVAILABILITY_UPDATE', '게임 공개·점검 설정', 'GAME', FALSE),
('SERVICE_STATUS_READ', '서비스 상태 조회', 'OPERATIONS', FALSE);

-- SUPER_ADMIN은 매핑을 편집하지 않고 애플리케이션에서 전체 권한을 부여한다.
INSERT INTO tbl_role_permission (role_code, permission_code) VALUES
('ADMIN', 'ADMIN_ACCESS'),
('ADMIN', 'DASHBOARD_READ'),
('ADMIN', 'USER_READ'),
('ADMIN', 'USER_STATUS_UPDATE'),
('ADMIN', 'USER_SESSION_REVOKE'),
('ADMIN', 'ACCESS_POLICY_READ'),
('ADMIN', 'ACCESS_POLICY_UPDATE'),
('ADMIN', 'CHAT_HISTORY_READ'),
('ADMIN', 'ACTIVITY_LOG_READ'),
('ADMIN', 'ADMIN_AUDIT_READ'),
('ADMIN', 'GAME_READ'),
('ADMIN', 'GAME_WRITE'),
('ADMIN', 'GAME_AVAILABILITY_UPDATE'),
('ADMIN', 'SERVICE_STATUS_READ'),
('MANAGER', 'ADMIN_ACCESS'),
('MANAGER', 'USER_READ'),
('MANAGER', 'CHAT_HISTORY_READ');

-- 기존 회원과 신규 가입은 모두 USER로 시작한다. 슈퍼 관리자 초기 배정은 별도 명시적 절차다.
ALTER TABLE tbl_user
    ADD COLUMN role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER' COMMENT '회원 역할',
    ADD COLUMN role_version BIGINT NOT NULL DEFAULT 0 COMMENT '회원 역할 편집 충돌 감지',
    ADD COLUMN super_admin_guard TINYINT GENERATED ALWAYS AS (CASE WHEN role_code = 'SUPER_ADMIN' THEN 1 ELSE NULL END) STORED,
    ADD CONSTRAINT fk_user_role FOREIGN KEY (role_code) REFERENCES tbl_role (code),
    ADD CONSTRAINT uk_user_single_super_admin UNIQUE (super_admin_guard),
    ADD CONSTRAINT ck_super_admin_active CHECK (role_code <> 'SUPER_ADMIN' OR (status = 'ACTIVE' AND deleted_at IS NULL));

-- 감사 기록은 업무 변경과 같은 MySQL 트랜잭션에서 추가한다. 회원/역할 삭제 후에도 기록을 보존한다.
CREATE TABLE tbl_admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NULL COMMENT '명시적인 초기 설정은 NULL인 시스템 작업',
    actor_role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    action VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    reason VARCHAR(300) NULL,
    detail JSON NOT NULL COMMENT '허용된 변경 정보만 기록. 비밀번호/토큰/개인정보 제외',
    occurred_at DATETIME(6) NOT NULL,
    INDEX idx_admin_audit_time (occurred_at, id),
    INDEX idx_admin_audit_actor (actor_user_id, occurred_at, id),
    INDEX idx_admin_audit_target (target_type, target_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리자 중요 작업 감사';

