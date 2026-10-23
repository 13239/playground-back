-- 게임의 표시 정보·장르·지원 모드를 분리한다. 오목의 방/대국/채팅 식별자는 변경하지 않는다.
CREATE TABLE tbl_game (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    summary VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    thumbnail_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    availability VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DISABLED',
    unavailable_message VARCHAR(300) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    first_published_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uk_game_code UNIQUE (code),
    CONSTRAINT ck_game_availability CHECK (availability IN ('AVAILABLE', 'MAINTENANCE', 'DISABLED')),
    CONSTRAINT ck_game_sort CHECK (sort_order BETWEEN 0 AND 1000000),
    INDEX idx_game_public_order (published, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게임 카탈로그와 전체 이용 정책';

CREATE TABLE tbl_game_genre (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uk_game_genre_code UNIQUE (code),
    CONSTRAINT ck_game_genre_sort CHECK (sort_order BETWEEN 0 AND 1000000),
    INDEX idx_game_genre_order (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='확장 가능한 게임 장르';

CREATE TABLE tbl_game_genre_mapping (
    game_id BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,
    PRIMARY KEY (game_id, genre_id),
    CONSTRAINT fk_game_genre_game FOREIGN KEY (game_id) REFERENCES tbl_game (id),
    CONSTRAINT fk_game_genre_genre FOREIGN KEY (genre_id) REFERENCES tbl_game_genre (id),
    INDEX idx_genre_game (genre_id, game_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게임의 복수 장르';

CREATE TABLE tbl_game_mode (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game_id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    graphics_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    connection_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interaction_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    min_human_players INT NOT NULL,
    max_human_players INT NOT NULL,
    definition_version INT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    availability VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DISABLED',
    unavailable_message VARCHAR(300) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_game_mode_game FOREIGN KEY (game_id) REFERENCES tbl_game (id),
    CONSTRAINT uk_game_mode_code UNIQUE (game_id, code),
    CONSTRAINT ck_game_mode_players CHECK (min_human_players >= 1 AND max_human_players >= min_human_players AND max_human_players <= 10000),
    CONSTRAINT ck_game_mode_graphics CHECK (graphics_type IN ('TWO_D', 'THREE_D')),
    CONSTRAINT ck_game_mode_connection CHECK (connection_type IN ('LOCAL', 'ONLINE')),
    CONSTRAINT ck_game_mode_interaction CHECK (interaction_type IN ('SOLO', 'COMPETITIVE', 'COOPERATIVE')),
    CONSTRAINT ck_game_mode_availability CHECK (availability IN ('AVAILABLE', 'MAINTENANCE', 'DISABLED')),
    CONSTRAINT ck_game_mode_definition CHECK (definition_version >= 1),
    CONSTRAINT ck_game_mode_sort CHECK (sort_order BETWEEN 0 AND 1000000),
    INDEX idx_game_mode_catalog (game_id, published, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게임 실행 모드와 모드별 이용 정책';

INSERT INTO tbl_game_genre (code, name, sort_order, created_at) VALUES
('PUZZLE', '퍼즐', 10, UTC_TIMESTAMP(6)), ('RACING', '레이싱', 20, UTC_TIMESTAMP(6)),
('ACTION', '액션', 30, UTC_TIMESTAMP(6)), ('BOARD', '보드', 40, UTC_TIMESTAMP(6)),
('SIMULATION', '시뮬레이션', 50, UTC_TIMESTAMP(6)), ('CASUAL', '캐주얼', 60, UTC_TIMESTAMP(6));

-- 현재 제공하는 오목만 등록하며 가상의 게임·접속 통계는 생성하지 않는다.
INSERT INTO tbl_game (code, name, summary, description, thumbnail_key, published, availability, first_published_at, created_at)
VALUES ('GOMOKU', '오목', '한 수 한 수, 함께 즐기는 두뇌 대결', 'AI와 혼자 연습하거나 다른 사용자와 온라인 대국을 즐겨 보세요.',
    'GOMOKU', TRUE, 'AVAILABLE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
INSERT INTO tbl_game_genre_mapping (game_id, genre_id)
SELECT g.id, r.id FROM tbl_game g CROSS JOIN tbl_game_genre r WHERE g.code = 'GOMOKU' AND r.code IN ('BOARD', 'CASUAL');
INSERT INTO tbl_game_mode (game_id, code, name, graphics_type, connection_type, interaction_type, min_human_players, max_human_players,
    definition_version, published, availability, sort_order, created_at)
SELECT id, 'AI', 'AI 대국', 'TWO_D', 'LOCAL', 'SOLO', 1, 1, 1, TRUE, 'AVAILABLE', 10, UTC_TIMESTAMP(6)
FROM tbl_game WHERE code = 'GOMOKU';
INSERT INTO tbl_game_mode (game_id, code, name, graphics_type, connection_type, interaction_type, min_human_players, max_human_players,
    definition_version, published, availability, sort_order, created_at)
SELECT id, 'ONLINE', '온라인 대국', 'TWO_D', 'ONLINE', 'COMPETITIVE', 2, 2, 1, TRUE, 'AVAILABLE', 20, UTC_TIMESTAMP(6)
FROM tbl_game WHERE code = 'GOMOKU';
