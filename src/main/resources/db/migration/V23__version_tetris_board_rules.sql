-- 기존 10×20 판은 v1로 남기고 새 판부터 15×22 규칙을 저장해 배포 전후 입력 재생이 섞이지 않게 한다.
ALTER TABLE tbl_tetris_run ADD COLUMN rule_version INT NOT NULL DEFAULT 1;
