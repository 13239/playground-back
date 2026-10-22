-- 대국 참가자는 기존 계정 고유 제약을 유지하고, 새로고침할 때만 같은 브라우저 탭으로 페이지 소유권을 이어받는다.
ALTER TABLE tbl_gomoku_room_participant
    ADD COLUMN tab_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '현재 플레이 중인 브라우저 탭 식별자';

-- 활성 대국만 (active, id) 단일 범위로 스캔해 수백만 건의 종료 이력에서 매 배치 정렬을 반복하지 않는다.
-- MySQL 생성 열은 phase만 참조하므로 AUTO_INCREMENT PK를 참조하는 제한에 걸리지 않는다.
ALTER TABLE tbl_gomoku_match
    ADD COLUMN active BOOLEAN GENERATED ALWAYS AS (CASE WHEN phase = 'FINISHED' THEN FALSE ELSE TRUE END) STORED,
    ADD INDEX idx_gomoku_match_active_id (active, id),
    ADD INDEX idx_gomoku_match_room_id (room_id, id);
