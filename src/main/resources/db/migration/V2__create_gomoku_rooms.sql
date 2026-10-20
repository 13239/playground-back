-- 오목 1단계: 방 목록과 생성 정보를 저장한다. 인원·판 크기·시간은 서버 규칙으로 고정한다.
CREATE TABLE tbl_gomoku_room (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '오목 방 pk',
    host_user_id BIGINT NOT NULL COMMENT '방장 회원 pk',
    create_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '중복 생성 방지 요청 식별자',
    title VARCHAR(30) NOT NULL COMMENT '방 제목',
    visibility VARCHAR(10) NOT NULL COMMENT 'PUBLIC 또는 PRIVATE',
    password_hash VARCHAR(255) NULL COMMENT '비공개 방 비밀번호 Argon2 해시',
    invite_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '서버에서 생성한 초대 코드',
    undo_limit SMALLINT NOT NULL COMMENT '무르기 허용 횟수 1, 3, 5',
    forbidden_rule VARCHAR(10) NOT NULL COMMENT 'NONE 또는 STANDARD',
    status VARCHAR(10) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING, PLAYING, CLOSED',
    player_count SMALLINT NOT NULL DEFAULT 1 COMMENT '현재 참여 인원',
    -- 닫힌 방의 이력을 보존하면서 계정별로 열린 방은 하나만 허용한다.
    active_host_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN status <> 'CLOSED' THEN host_user_id ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL COMMENT '생성시간',
    updated_at DATETIME(6) NULL COMMENT '수정시간',
    deleted_at DATETIME(6) NULL COMMENT '방을 닫은 시간',
    PRIMARY KEY (id),
    CONSTRAINT fk_gomoku_room_host FOREIGN KEY (host_user_id) REFERENCES tbl_user (id),
    CONSTRAINT uk_gomoku_room_invite_code UNIQUE (invite_code),
    CONSTRAINT uk_gomoku_room_request UNIQUE (host_user_id, create_request_id),
    CONSTRAINT uk_gomoku_room_active_host UNIQUE (active_host_user_id),
    CONSTRAINT ck_gomoku_room_undo CHECK (undo_limit IN (1, 3, 5)),
    CONSTRAINT ck_gomoku_room_players CHECK (player_count BETWEEN 1 AND 2),
    CONSTRAINT ck_gomoku_room_rule CHECK (forbidden_rule IN ('NONE', 'STANDARD')),
    CONSTRAINT ck_gomoku_room_status CHECK (status IN ('WAITING', 'PLAYING', 'CLOSED')),
    CONSTRAINT ck_gomoku_room_password CHECK (
        (visibility = 'PUBLIC' AND password_hash IS NULL)
        OR (visibility = 'PRIVATE' AND password_hash IS NOT NULL)
    ),
    INDEX idx_gomoku_room_status_id (status, id),
    INDEX idx_gomoku_room_visibility_status_id (visibility, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='오목 방';
