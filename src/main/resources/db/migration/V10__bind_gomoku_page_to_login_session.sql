-- 종료 알림을 보내지 못한 브라우저의 점유를 새 로그인에서 복구한다. 기존 참가·대국 데이터는 유지한다.
-- 로그인 유효성은 Redis에서 검증하며, 이 값은 API 응답에 노출하지 않는 내부 점유 식별자다.
ALTER TABLE tbl_gomoku_room_participant
    ADD COLUMN login_session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '페이지를 점유한 로그인 세션 식별자';
