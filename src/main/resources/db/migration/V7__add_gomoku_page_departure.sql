-- 페이지별 종료 예약을 참가 이력에 결합한다. 새로고침이 먼저 도착해도 이전 페이지의 늦은 종료가 새 화면을 내보내지 못한다.
ALTER TABLE tbl_gomoku_room_participant
    ADD COLUMN page_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '현재 참가 화면 식별자',
    ADD COLUMN exit_deadline DATETIME(6) NULL COMMENT '새로고침 복귀를 기다리는 종료 마감',
    ADD INDEX idx_gomoku_participant_exit (exit_deadline, id);
