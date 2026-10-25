-- 기존 노출 순서와 같은 값의 id 순서를 보존하면서 드래그 이동에 사용할 연속 순번으로 정규화한다.
UPDATE tbl_game g
JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY sort_order, id) - 1 AS position FROM tbl_game) ordered
    ON ordered.id = g.id
SET g.sort_order = ordered.position, g.version = g.version + 1, g.updated_at = UTC_TIMESTAMP(6)
WHERE g.sort_order <> ordered.position;

UPDATE tbl_game_genre g
JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY sort_order, id) - 1 AS position FROM tbl_game_genre) ordered
    ON ordered.id = g.id
SET g.sort_order = ordered.position, g.version = g.version + 1, g.updated_at = UTC_TIMESTAMP(6)
WHERE g.sort_order <> ordered.position;

-- 공개 여부와 관계없는 관리자 순서 조회·범위 이동에도 정렬 인덱스를 사용한다.
CREATE INDEX idx_game_grid_order ON tbl_game (sort_order, id);
CREATE INDEX idx_game_genre_grid_order ON tbl_game_genre (sort_order, id);

CREATE TABLE tbl_catalog_order_state (
    type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_catalog_order_type CHECK (type IN ('GAME', 'GENRE')),
    CONSTRAINT ck_catalog_order_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='전체 게임·장르 목록의 순서 버전과 잠금';

INSERT INTO tbl_catalog_order_state (type) VALUES ('GAME'), ('GENRE');

-- 모드 노출 순서는 배포된 구현의 정의 순서를 사용하며 수동 정렬값은 더 이상 저장하지 않는다.
ALTER TABLE tbl_game_mode
    DROP INDEX idx_game_mode_catalog,
    DROP CHECK ck_game_mode_sort,
    DROP COLUMN sort_order,
    ADD INDEX idx_game_mode_catalog (game_id, published, id);
