-- 1인·2인 모두 별도 게임 서버가 전투를 판정한다. 기존 목록 순서를 유지한다.
INSERT INTO tbl_game(code,name,summary,description,published,availability,sort_order,first_published_at,created_at)
SELECT 'SHOOTING','슈팅 서바이벌','함께 싸우고, 강화하고, 더 높은 스테이지로',
 '직접 조준하는 탑다운 슈팅. 네 가지 무기와 강화 조합으로 1인 기록 도전과 2인 협동을 즐겨보세요.',
 TRUE,'AVAILABLE',COALESCE(MAX(sort_order),-1)+1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM tbl_game;
INSERT INTO tbl_game_mode(game_id,code,name,graphics_type,connection_type,interaction_type,min_human_players,max_human_players,definition_version,published,availability,created_at)
SELECT id,'SINGLE','1인 기록 도전','TWO_D','ONLINE','SOLO',1,1,1,TRUE,'AVAILABLE',UTC_TIMESTAMP(6) FROM tbl_game WHERE code='SHOOTING';
INSERT INTO tbl_game_mode(game_id,code,name,graphics_type,connection_type,interaction_type,min_human_players,max_human_players,definition_version,published,availability,created_at)
SELECT id,'COOP','2인 협동','TWO_D','ONLINE','COOPERATIVE',2,2,1,TRUE,'AVAILABLE',UTC_TIMESTAMP(6) FROM tbl_game WHERE code='SHOOTING';
INSERT INTO tbl_game_genre_mapping(game_id,genre_id)
SELECT g.id,f.id FROM tbl_game g CROSS JOIN tbl_game_filter f WHERE g.code='SHOOTING' AND f.type='GENRE' AND f.code IN('SHOOTING','ACTION','CASUAL');
INSERT INTO tbl_game_card_label(game_id,label_code)
SELECT g.id,f.label_code FROM tbl_game g CROSS JOIN tbl_game_filter f WHERE g.code='SHOOTING' AND f.enabled=TRUE AND
 (f.type='GENRE' AND f.code IN('SHOOTING','ACTION') OR f.type='PLAYERS' AND f.min_players IN(1,2) AND f.max_players IN(1,2) OR f.type='GRAPHICS' AND f.graphics_type='TWO_D');
UPDATE tbl_catalog_order_state SET version=version+1 WHERE type='GAME';

-- 성장 상태나 이어하기 데이터 없이 검증된 마지막 클리어 기록만 보존한다.
CREATE TABLE tbl_shooting_record(
 id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 run_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id BIGINT NOT NULL,mode VARCHAR(10) NOT NULL,stage INT NOT NULL DEFAULT 0,
 combat_millis BIGINT NOT NULL DEFAULT 0,teammate VARCHAR(50),finished BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL,achieved_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_shooting_record_user FOREIGN KEY(user_id) REFERENCES tbl_user(id),
 CONSTRAINT uk_shooting_record_run_user UNIQUE(run_id,user_id),
 CONSTRAINT ck_shooting_record_values CHECK(stage>=0 AND combat_millis>=0 AND mode IN('SINGLE','COOP')),
 INDEX idx_shooting_ranking(mode,stage DESC,combat_millis,user_id),
 INDEX idx_shooting_personal(user_id,mode,stage DESC,combat_millis)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
