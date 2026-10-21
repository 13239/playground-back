-- 오목 대기실은 실제 참가자 두 명만 관리한다. 기존 방과 회원 데이터는 보존한다.
ALTER TABLE tbl_gomoku_room
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 COMMENT '참가·준비 상태 변경 순서',
    DROP CHECK ck_gomoku_room_players,
    ADD CONSTRAINT ck_gomoku_room_players CHECK (
        (status = 'CLOSED' AND player_count BETWEEN 0 AND 2)
        OR (status <> 'CLOSED' AND player_count BETWEEN 1 AND 2)
    );

CREATE TABLE tbl_gomoku_room_participant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    membership_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '재입장마다 새로 발급하는 참가 식별자',
    seat SMALLINT NOT NULL COMMENT '공개 좌석 번호 1 또는 2',
    ready BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at_micros BIGINT NOT NULL COMMENT 'Redis 시계로 확정한 채팅 공개 시작 경계',
    last_seen_at DATETIME(6) NOT NULL COMMENT '대기실 접속 확인 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_gomoku_participant_room FOREIGN KEY (room_id) REFERENCES tbl_gomoku_room (id),
    CONSTRAINT fk_gomoku_participant_user FOREIGN KEY (user_id) REFERENCES tbl_user (id),
    CONSTRAINT uk_gomoku_participant_user UNIQUE (user_id),
    CONSTRAINT uk_gomoku_participant_seat UNIQUE (room_id, seat),
    CONSTRAINT uk_gomoku_participant_membership UNIQUE (membership_id),
    CONSTRAINT ck_gomoku_participant_seat CHECK (seat IN (1, 2)),
    INDEX idx_gomoku_participant_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='현재 오목 대기실 참가자';

-- 1단계에서 만든 열린 방의 방장도 대기실로 바로 복귀할 수 있다.
INSERT INTO tbl_gomoku_room_participant (room_id, user_id, membership_id, seat, ready, joined_at_micros, last_seen_at)
SELECT id, host_user_id, UUID(), 1, FALSE,
       CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6)) * 1000000 AS UNSIGNED), CURRENT_TIMESTAMP(6)
FROM tbl_gomoku_room WHERE status <> 'CLOSED';
