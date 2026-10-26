-- 기존 장르의 ID·게임 연결을 보존하고 공통 필터 목록으로 확장한다. 운영 DB 실행은 Flyway가 담당한다.
RENAME TABLE tbl_game_genre TO tbl_game_filter;
-- 선택 라벨 검색·연결 수 집계는 게임별 PK와 반대 방향의 인덱스를 사용한다.
CREATE INDEX idx_game_card_label_filter ON tbl_game_card_label(label_code,game_id);
ALTER TABLE tbl_game_filter
 ADD COLUMN type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'GENRE',
 ADD COLUMN label_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN icon VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'gamepad',
 ADD COLUMN min_players INT NULL, ADD COLUMN max_players INT NULL,
 ADD COLUMN graphics_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE tbl_game_filter SET icon = CASE code
 WHEN 'PUZZLE' THEN 'puzzle' WHEN 'BOARD' THEN 'board' WHEN 'ACTION' THEN 'swords'
 WHEN 'RACING' THEN 'car' WHEN 'SIMULATION' THEN 'city' ELSE 'gamepad' END;

-- 기존 사용자 장르 코드와 충돌하면 새 항목에만 UUID 접미사를 붙인다. 화면은 DB 코드를 그대로 사용한다.
SET @filter_start = (SELECT COALESCE(MAX(sort_order), -1) + 1 FROM tbl_game_filter);
INSERT INTO tbl_game_filter (code,name,type,icon,enabled,sort_order,min_players,max_players,graphics_type,created_at,version)
SELECT IF(EXISTS(SELECT 1 FROM tbl_game_filter f WHERE f.code = seed.code),
 CONCAT(seed.code,'_',REPLACE(UUID(),'-','')),seed.code),
 seed.name,seed.type,seed.icon,FALSE,@filter_start + seed.position,seed.min_players,seed.max_players,seed.graphics_type,UTC_TIMESTAMP(6),0
FROM (
 SELECT 'SINGLE' AS code,'1인' AS name,'PLAYERS' AS type,'user' AS icon,0 AS position,1 AS min_players,1 AS max_players,NULL AS graphics_type
 UNION ALL SELECT 'TWO','2인','PLAYERS','users',1,2,2,NULL
 UNION ALL SELECT 'GROUP','3인 이상','PLAYERS','users',2,3,10000,NULL
 UNION ALL SELECT 'TWO_D','2D','GRAPHICS','2d',3,NULL,NULL,'TWO_D'
 UNION ALL SELECT 'THREE_D','3D','GRAPHICS','cube',4,NULL,NULL,'THREE_D'
) seed;

-- 이전에 명시적으로 선택한 사용자 정의 인원 범위도 필터로 옮겨 선택 정보를 잃지 않는다.
SET @range_start = (SELECT MAX(sort_order) + 1 FROM tbl_game_filter);
INSERT INTO tbl_game_filter (code,name,type,icon,enabled,sort_order,min_players,max_players,created_at,version)
SELECT CONCAT('PLAYERS_',REPLACE(UUID(),'-','')),
 IF(r.minimum = r.maximum,CONCAT(r.minimum,'인'),CONCAT(r.minimum,'~',r.maximum,'인')),
 'PLAYERS','users',FALSE,@range_start + ROW_NUMBER() OVER (ORDER BY r.minimum,r.maximum) - 1,r.minimum,r.maximum,UTC_TIMESTAMP(6),0
FROM (
 SELECT DISTINCT CAST(SUBSTRING_INDEX(SUBSTRING(label_code,9),'-',1) AS UNSIGNED) AS minimum,
 CAST(SUBSTRING_INDEX(label_code,'-',-1) AS UNSIGNED) AS maximum
 FROM tbl_game_card_label WHERE label_code REGEXP '^PLAYERS:[0-9]+-[0-9]+$'
) r WHERE NOT EXISTS(SELECT 1 FROM tbl_game_filter f WHERE f.type='PLAYERS' AND f.min_players=r.minimum AND f.max_players=r.maximum);
UPDATE tbl_game_filter SET label_code = CONCAT(type,':',code);
ALTER TABLE tbl_game_filter
 MODIFY COLUMN label_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 ADD CONSTRAINT uk_game_filter_label UNIQUE(label_code),
 ADD CONSTRAINT ck_game_filter_type CHECK(type IN ('GENRE','PLAYERS','GRAPHICS')),
 ADD CONSTRAINT ck_game_filter_binding CHECK(
 (type='GENRE' AND min_players IS NULL AND max_players IS NULL AND graphics_type IS NULL) OR
 (type='PLAYERS' AND min_players IS NOT NULL AND max_players IS NOT NULL AND min_players BETWEEN 1 AND 10000 AND max_players BETWEEN min_players AND 10000 AND graphics_type IS NULL) OR
 (type='GRAPHICS' AND min_players IS NULL AND max_players IS NULL AND graphics_type IS NOT NULL AND graphics_type IN ('TWO_D','THREE_D'))),
 ADD INDEX idx_game_filter_catalog(type,enabled,sort_order,id);

-- 화면 표시값 대신 영구 필터 키를 저장한다. 같은 이름의 다른 유형과 선택이 섞이지 않는다.
INSERT IGNORE INTO tbl_game_card_label(game_id,label_code)
 SELECT selected.game_id,f.label_code FROM tbl_game_card_label selected JOIN tbl_game_filter f
 ON f.type='PLAYERS' AND selected.label_code=CONCAT('PLAYERS:',f.min_players,'-',f.max_players);
INSERT IGNORE INTO tbl_game_card_label(game_id,label_code)
 SELECT selected.game_id,f.label_code FROM tbl_game_card_label selected JOIN tbl_game_filter f
 ON f.type='GRAPHICS' AND selected.label_code=CONCAT('GRAPHICS:',f.graphics_type);
DELETE selected FROM tbl_game_card_label selected
 LEFT JOIN tbl_game_filter f ON f.label_code=selected.label_code WHERE f.id IS NULL;
-- 신규 인원·그래픽의 사용 상태는 이전에 명시적으로 선택한 라벨만 복원한다.
UPDATE tbl_game_filter f SET enabled=TRUE WHERE type<>'GENRE'
 AND EXISTS(SELECT 1 FROM tbl_game_card_label selected WHERE selected.label_code=f.label_code);
UPDATE tbl_game SET version=version+1;

-- 기존 순서와 잠금 버전을 이어받아 열려 있던 편집 화면의 덮어쓰기를 차단한다.
ALTER TABLE tbl_catalog_order_state DROP CHECK ck_catalog_order_type;
UPDATE tbl_catalog_order_state SET type='FILTER',version=version+1 WHERE type='GENRE';
ALTER TABLE tbl_catalog_order_state ADD CONSTRAINT ck_catalog_order_type CHECK(type IN ('GAME','FILTER'));
