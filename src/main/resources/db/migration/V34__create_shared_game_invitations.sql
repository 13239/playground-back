-- 새 게임의 초대 수명·중복 요청·수락 경합을 공통 저장소에서 관리한다. 게임별 방은 어댑터가 검증한다.
CREATE TABLE tbl_game_invitation (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 game_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 room_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 room_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 inviter_id BIGINT NOT NULL,
 inviter_session CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 target_id BIGINT NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 state VARCHAR(12) NOT NULL,
 CONSTRAINT uk_game_invitation_request UNIQUE(inviter_id,request_id),
 CONSTRAINT fk_game_invitation_inviter FOREIGN KEY(inviter_id) REFERENCES tbl_user(id),
 CONSTRAINT fk_game_invitation_target FOREIGN KEY(target_id) REFERENCES tbl_user(id),
 CONSTRAINT ck_game_invitation_state CHECK(state IN('PENDING','ACCEPTED','REJECTED','CANCELLED','EXPIRED')),
 INDEX idx_game_invitation_target(target_id,state,expires_at),
 INDEX idx_game_invitation_inviter(inviter_id,state,expires_at),
 INDEX idx_game_invitation_room(game_code,room_id,state,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
