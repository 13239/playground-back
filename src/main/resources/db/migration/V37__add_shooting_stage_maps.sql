-- 기존 무한 도전 기록은 LEGACY로 보존하고 새 스테이지 기록과 구분한다.
ALTER TABLE tbl_shooting_record
    ADD COLUMN map_id VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN rule_version INT NOT NULL DEFAULT 2,
    ADD COLUMN stars TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN completed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD INDEX idx_shooting_stage_progress (user_id, rule_version, completed, map_id, mode, combat_millis),
    ADD INDEX idx_shooting_stage_ranking (map_id, rule_version, mode, stage DESC, combat_millis);

-- 기본 소개만 새 완주 목표로 바꾼다. 관리자가 작성한 별도 소개는 보존한다.
UPDATE tbl_game SET name='슈팅 스테이지 도전',
    summary='서로 다른 3개 맵을 공략하고 별 3개에 도전하세요',
    description='10스테이지 완주형 탑다운 슈팅. 다섯 캐릭터와 네 가지 무기로 맵별 장치·보스를 공략하고 1인·2인 협동 기록에 도전하세요.'
WHERE code='SHOOTING' AND name='슈팅 서바이벌';
