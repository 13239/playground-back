-- 운영 편집 버전은 역할 배정 버전과 구분한다. 기존 회원/비밀번호와 과거 닉네임을 추측해 변경하지 않는다.
ALTER TABLE tbl_user
    ADD COLUMN management_version BIGINT NOT NULL DEFAULT 0 COMMENT '회원 정보 편집 충돌 방지',
    ADD INDEX idx_user_management_status (deleted_at, status, id),
    ADD INDEX idx_user_management_role (role_code, deleted_at, id),
    ADD INDEX idx_user_nickname_search (nickname, id);

-- 본인이 실제 변경한 이력만 같은 트랜잭션에 추가한다. 현재 이름을 과거 변경 이력으로 소급 생성하지 않는다.
CREATE TABLE tbl_user_nickname_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    previous_nickname VARCHAR(50) NOT NULL,
    new_nickname VARCHAR(50) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    INDEX idx_nickname_history_user (user_id, id),
    CONSTRAINT fk_nickname_history_user FOREIGN KEY (user_id) REFERENCES tbl_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='본인 닉네임 변경 이력';
