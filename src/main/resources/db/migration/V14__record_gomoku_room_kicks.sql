-- 강퇴된 회원의 동일 방 재입장을 금지한다. 방장은 방 행 잠금 안에서 이력을 남긴다.
CREATE TABLE tbl_gomoku_room_kick (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_gomoku_room_kick_room FOREIGN KEY (room_id) REFERENCES tbl_gomoku_room (id),
    CONSTRAINT fk_gomoku_room_kick_user FOREIGN KEY (user_id) REFERENCES tbl_user (id),
    CONSTRAINT uk_gomoku_room_kick_user UNIQUE (room_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='오목 대기실 강퇴 이력';
