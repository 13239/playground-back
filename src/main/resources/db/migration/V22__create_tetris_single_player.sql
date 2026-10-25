-- 테트리스는 로컬 1인 모드만 배포한다. 게임 목록의 기존 순서를 보존한다.
INSERT INTO tbl_game (code, name, summary, description, published, availability, sort_order, first_published_at, created_at)
SELECT 'TETRIS', '테트리스', '블록을 쌓고, 나만의 최고 기록에 도전하세요',
       '홀드와 다음 블록을 활용하는 1인 테트리스. 라인을 지우며 최고 점수에 도전하세요.',
       TRUE, 'AVAILABLE', COALESCE(MAX(sort_order), -1) + 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM tbl_game;
INSERT INTO tbl_game_mode (game_id, code, name, graphics_type, connection_type, interaction_type,
    min_human_players, max_human_players, definition_version, published, availability, created_at)
SELECT id, 'SINGLE', '싱글 플레이', 'TWO_D', 'LOCAL', 'SOLO', 1, 1, 1, TRUE, 'AVAILABLE', UTC_TIMESTAMP(6)
FROM tbl_game WHERE code = 'TETRIS';
INSERT INTO tbl_game_genre_mapping (game_id, genre_id)
SELECT g.id, r.id FROM tbl_game g CROSS JOIN tbl_game_genre r WHERE g.code = 'TETRIS' AND r.code IN ('PUZZLE', 'CASUAL');
UPDATE tbl_catalog_order_state SET version = version + 1 WHERE type = 'GAME';

-- 계정당 진행 정보 한 건만 보관한다. 다시 시작한 이전 판의 늦은 제출은 새 판을 덮어쓸 수 없다.
CREATE TABLE tbl_tetris_run (
    user_id BIGINT NOT NULL PRIMARY KEY,
    run_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    login_session_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    seed BIGINT NOT NULL DEFAULT 1,
    started_at DATETIME(6) NULL,
    finished BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_tetris_run_user FOREIGN KEY (user_id) REFERENCES tbl_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 서버가 재생 검증한 점수만 반영하며 닉네임은 회원 테이블의 최신 값을 조회한다.
CREATE TABLE tbl_tetris_player_stats (
    user_id BIGINT NOT NULL PRIMARY KEY,
    best_score BIGINT NOT NULL DEFAULT 0,
    plays BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tetris_stats_user FOREIGN KEY (user_id) REFERENCES tbl_user(id),
    CONSTRAINT ck_tetris_stats_nonnegative CHECK (best_score >= 0 AND plays >= 0),
    INDEX idx_tetris_ranking (best_score DESC, user_id ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
