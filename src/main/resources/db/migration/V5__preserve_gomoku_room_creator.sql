-- 방장 위임과 생성 요청의 소유자는 다르다. 최초 생성자를 보존해 이전 요청이 다른 사람의 방을 재공개하지 않게 한다.
-- Instant로 읽는 접속 확인 열은 DB 서버의 로컬 시간대와 무관하게 UTC로 초기화한다.
UPDATE tbl_gomoku_room_participant SET last_seen_at = UTC_TIMESTAMP(6);
ALTER TABLE tbl_gomoku_room ADD COLUMN creator_user_id BIGINT NULL COMMENT '변경하지 않는 최초 방 생성자';
UPDATE tbl_gomoku_room SET creator_user_id = host_user_id WHERE creator_user_id IS NULL;
-- 이전 복합 고유 인덱스가 방장 FK의 인덱스도 맡고 있으므로 대체 인덱스를 먼저 준비한다.
ALTER TABLE tbl_gomoku_room ADD INDEX idx_gomoku_room_host (host_user_id);
ALTER TABLE tbl_gomoku_room
    MODIFY COLUMN creator_user_id BIGINT NOT NULL COMMENT '변경하지 않는 최초 방 생성자',
    ADD CONSTRAINT fk_gomoku_room_creator FOREIGN KEY (creator_user_id) REFERENCES tbl_user (id),
    DROP INDEX uk_gomoku_room_request,
    ADD CONSTRAINT uk_gomoku_room_request UNIQUE (creator_user_id, create_request_id);
