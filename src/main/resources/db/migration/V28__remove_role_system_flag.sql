-- ROOT 삭제·편집 보호는 변경할 수 없는 역할 코드로 판단한다.
-- 기존 역할·회원 배정·기능 매핑·표시 순서는 보존하고 더 이상 사용하지 않는 구분 플래그만 제거한다.
ALTER TABLE tbl_role DROP COLUMN system_role;
