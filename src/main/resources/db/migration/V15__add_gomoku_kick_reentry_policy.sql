-- 기존 V14 강퇴 이력은 모두 재입장 금지로 유지하고, 새 강퇴에만 재입장 허용을 선택할 수 있게 한다.
ALTER TABLE tbl_gomoku_room_kick
    ADD COLUMN reentry_blocked TINYINT(1) NOT NULL DEFAULT 1 COMMENT '동일 방 재입장 금지 여부';
