-- 30장·15쌍의 서버 판정 게임. 1인 기록과 2인 승패만 보존하며 무승부는 없다.
INSERT INTO tbl_game(code,name,summary,description,published,availability,sort_order,first_published_at,created_at)
SELECT 'MEMORY','같은 그림 찾기','귀여운 친구들을 기억하고 같은 그림을 찾아보세요',
 '30장의 카드, 15쌍의 귀여운 캐릭터. 1인 기록 도전과 친구와의 2인 대전을 즐겨보세요.',TRUE,'AVAILABLE',COALESCE(MAX(sort_order),-1)+1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM tbl_game;
INSERT INTO tbl_game_mode(game_id,code,name,graphics_type,connection_type,interaction_type,min_human_players,max_human_players,definition_version,published,availability,created_at)
SELECT id,'SINGLE','1인 기록 도전','TWO_D','ONLINE','SOLO',1,1,1,TRUE,'AVAILABLE',UTC_TIMESTAMP(6) FROM tbl_game WHERE code='MEMORY';
INSERT INTO tbl_game_mode(game_id,code,name,graphics_type,connection_type,interaction_type,min_human_players,max_human_players,definition_version,published,availability,created_at)
SELECT id,'ONLINE','2인 대전','TWO_D','ONLINE','COMPETITIVE',2,2,1,TRUE,'AVAILABLE',UTC_TIMESTAMP(6) FROM tbl_game WHERE code='MEMORY';
INSERT INTO tbl_game_genre_mapping(game_id,genre_id) SELECT g.id,f.id FROM tbl_game g CROSS JOIN tbl_game_filter f WHERE g.code='MEMORY' AND f.type='GENRE' AND f.code IN('PUZZLE','CASUAL');
INSERT INTO tbl_game_card_label(game_id,label_code) SELECT g.id,f.label_code FROM tbl_game g CROSS JOIN tbl_game_filter f WHERE g.code='MEMORY' AND f.enabled=TRUE AND (f.type='GENRE' AND f.code IN('PUZZLE','CASUAL') OR f.type='PLAYERS' AND f.min_players IN(1,2) AND f.max_players IN(1,2) OR f.type='GRAPHICS' AND f.graphics_type='TWO_D');
UPDATE tbl_catalog_order_state SET version=version+1 WHERE type='GAME';

CREATE TABLE tbl_memory_room(
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_id VARCHAR(36) NOT NULL UNIQUE,code VARCHAR(6) NOT NULL UNIQUE,title VARCHAR(30) NOT NULL,password_hash VARCHAR(255),
 solo BOOLEAN NOT NULL,host_id BIGINT NOT NULL,guest_id BIGINT,host_login VARCHAR(36),guest_login VARCHAR(36),host_tab VARCHAR(36),guest_tab VARCHAR(36),
 host_seen DATETIME(6),guest_seen DATETIME(6),host_ready BOOLEAN NOT NULL DEFAULT FALSE,guest_ready BOOLEAN NOT NULL DEFAULT FALSE,
 host_rematch BOOLEAN NOT NULL DEFAULT FALSE,guest_rematch BOOLEAN NOT NULL DEFAULT FALSE,closed BOOLEAN NOT NULL DEFAULT FALSE,revision BIGINT NOT NULL DEFAULT 0,created_at DATETIME(6) NOT NULL,
 deck VARCHAR(30),owners VARCHAR(30) NOT NULL DEFAULT '000000000000000000000000000000',phase VARCHAR(12) NOT NULL DEFAULT 'WAITING',match_id VARCHAR(36),
 started_at DATETIME(6),deadline DATETIME(6),finished_at DATETIME(6),first_card INT NOT NULL DEFAULT -1,second_card INT NOT NULL DEFAULT -1,
 turn INT NOT NULL DEFAULT 1,first_seat INT NOT NULL DEFAULT 1,score_one INT NOT NULL DEFAULT 0,score_two INT NOT NULL DEFAULT 0,attempts INT NOT NULL DEFAULT 0,
 winner INT NOT NULL DEFAULT 0,finish_reason VARCHAR(16),recorded BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_memory_host FOREIGN KEY(host_id) REFERENCES tbl_user(id),CONSTRAINT fk_memory_guest FOREIGN KEY(guest_id) REFERENCES tbl_user(id),
 CONSTRAINT ck_memory_scores CHECK(score_one>=0 AND score_two>=0 AND score_one+score_two<=15),
 INDEX idx_memory_host(host_id,closed),INDEX idx_memory_guest(guest_id,closed),INDEX idx_memory_waiting(closed,solo,phase,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE tbl_memory_stats(
 user_id BIGINT NOT NULL PRIMARY KEY,best_millis BIGINT,attempts INT NOT NULL DEFAULT 0,achieved_at DATETIME(6),wins BIGINT NOT NULL DEFAULT 0,losses BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_memory_stats FOREIGN KEY(user_id) REFERENCES tbl_user(id),
 CONSTRAINT ck_memory_stats CHECK((best_millis IS NULL OR best_millis>=0) AND wins>=0 AND losses>=0),
 INDEX idx_memory_ranking(best_millis,attempts,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE tbl_memory_invitation(
 id VARCHAR(36) NOT NULL PRIMARY KEY,request_id VARCHAR(36) NOT NULL,room_id BIGINT NOT NULL,inviter_id BIGINT NOT NULL,target_id BIGINT NOT NULL,
 expires_at DATETIME(6) NOT NULL,state VARCHAR(12) NOT NULL,
 CONSTRAINT fk_memory_invite_room FOREIGN KEY(room_id) REFERENCES tbl_memory_room(id),
 CONSTRAINT fk_memory_inviter FOREIGN KEY(inviter_id) REFERENCES tbl_user(id),CONSTRAINT fk_memory_target FOREIGN KEY(target_id) REFERENCES tbl_user(id),
 UNIQUE KEY uk_memory_invite_retry(inviter_id,request_id),INDEX idx_memory_invite_target(target_id,state,expires_at),INDEX idx_memory_invite_room(room_id,state,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

