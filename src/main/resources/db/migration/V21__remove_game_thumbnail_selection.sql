-- 표지는 배포된 프론트의 게임 코드별 구현에서 선택한다. 기존 게임·장르 연결과 정렬·이용 상태는 보존한다.
ALTER TABLE tbl_game DROP COLUMN thumbnail_key;
