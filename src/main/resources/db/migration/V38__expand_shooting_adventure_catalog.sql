-- 관리자가 바꾼 소개는 보존하고 이전 기본 문구만 6개 맵 모험 모드로 갱신한다.
-- 기존 완주 기록은 버전 3 그대로 보존하며 호환 조회하므로 별·해금 데이터의 일괄 변경은 하지 않는다.
UPDATE tbl_game
SET name = '슈팅게임 · 모험 모드'
WHERE code = 'SHOOTING' AND name = '슈팅 스테이지 도전';

UPDATE tbl_game
SET summary = '수정 동굴부터 용암 성채까지 6개 맵을 공략하고 별을 모아보세요'
WHERE code = 'SHOOTING' AND summary = '서로 다른 3개 맵을 공략하고 별 3개에 도전하세요';
