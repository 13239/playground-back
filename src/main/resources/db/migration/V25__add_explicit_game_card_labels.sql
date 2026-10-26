-- 홈 카드의 노출 라벨을 장르 연결·플레이 모드와 분리하고 선택한 코드만 저장한다.
CREATE TABLE tbl_game_card_label (
    game_id BIGINT NOT NULL,
    label_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (game_id, label_code),
    CONSTRAINT fk_game_card_label_game FOREIGN KEY (game_id) REFERENCES tbl_game (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리자가 선택한 홈 게임 카드 라벨';

-- 기존에 선택한 장르는 유지한다. 그래픽·인원 라벨은 관리자가 명시적으로 선택하기 전에는 추가하지 않는다.
INSERT INTO tbl_game_card_label (game_id, label_code)
SELECT mapping.game_id, CONCAT('GENRE:', genre.code)
FROM tbl_game_genre_mapping mapping
JOIN tbl_game_genre genre ON genre.id = mapping.genre_id;

-- 이전 편집 화면이 새 라벨 설정을 덮어쓰지 않도록 관리 버전도 함께 갱신한다.
UPDATE tbl_game SET version = version + 1;
