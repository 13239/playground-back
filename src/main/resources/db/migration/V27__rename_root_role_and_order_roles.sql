-- 이미 사용 중인 ROOT 코드를 최고 관리자로 덮어쓰지 않고, 충돌 시 스키마 변경 전에 중단한다.
CREATE TEMPORARY TABLE tmp_root_role_migration_check (
    target_available BOOLEAN NOT NULL,
    source_valid BOOLEAN NOT NULL,
    CONSTRAINT ck_root_role_target_available CHECK (target_available = TRUE),
    CONSTRAINT ck_root_role_source_valid CHECK (source_valid = TRUE)
);
INSERT INTO tmp_root_role_migration_check (target_available, source_valid)
SELECT NOT EXISTS (SELECT 1 FROM tbl_role WHERE code = 'ROOT'),
       EXISTS (SELECT 1 FROM tbl_role WHERE code = 'SUPER_ADMIN' AND system_role = TRUE);
DROP TEMPORARY TABLE tmp_root_role_migration_check;

-- 추가 역할은 기본 4개 뒤에 표시하며, 번호 페이지의 동일 순서 항목은 고유 코드로 구분한다.
ALTER TABLE tbl_role
    ADD COLUMN sort_order INT NOT NULL DEFAULT 4 COMMENT '권한 목록 표시 순서',
    ADD CONSTRAINT ck_role_sort_order CHECK (sort_order >= 0),
    ADD INDEX idx_role_grid_order (sort_order, code);

-- 이전 중에도 두 코드에 걸쳐 최고 관리자 한 명·활성 계정 보호를 계속 강제한다.
ALTER TABLE tbl_user
    DROP CHECK ck_super_admin_active,
    CHANGE COLUMN super_admin_guard root_guard TINYINT
        GENERATED ALWAYS AS (CASE WHEN role_code IN ('SUPER_ADMIN', 'ROOT') THEN 1 ELSE NULL END) STORED,
    RENAME INDEX uk_user_single_super_admin TO uk_user_single_root,
    ADD CONSTRAINT ck_root_active CHECK (role_code NOT IN ('SUPER_ADMIN', 'ROOT') OR (status = 'ACTIVE' AND deleted_at IS NULL));
ALTER TABLE tbl_access_policy_exemption
    DROP CHECK ck_access_exemption_not_super,
    ADD CONSTRAINT ck_access_exemption_not_root CHECK (role_code NOT IN ('SUPER_ADMIN', 'ROOT'));

-- FK 검사를 끄지 않고 새 부모 행을 만든 뒤 기존 회원·기능 매핑을 같은 트랜잭션으로 옮긴다.
START TRANSACTION;
INSERT INTO tbl_role (code, name, description, system_role, sort_order, version, created_at, updated_at)
SELECT 'ROOT', 'ROOT', description, system_role, 0, version + 1, created_at, UTC_TIMESTAMP(6)
FROM tbl_role WHERE code = 'SUPER_ADMIN';

UPDATE tbl_user
SET role_code = 'ROOT', role_version = role_version + 1,
    management_version = management_version + 1, updated_at = UTC_TIMESTAMP(6)
WHERE role_code = 'SUPER_ADMIN';
UPDATE tbl_role_permission SET role_code = 'ROOT' WHERE role_code = 'SUPER_ADMIN';
DELETE FROM tbl_role WHERE code = 'SUPER_ADMIN';

-- 기본 역할의 표시 이름·순서를 DB 원본에서 바꾸며 사용자 정의 역할과 과거 감사 기록은 보존한다.
UPDATE tbl_role
SET name = CASE code WHEN 'ADMIN' THEN '관리자' WHEN 'MANAGER' THEN '매니저' WHEN 'USER' THEN '유저' END,
    sort_order = CASE code WHEN 'ADMIN' THEN 1 WHEN 'MANAGER' THEN 2 WHEN 'USER' THEN 3 END,
    version = version + 1, updated_at = UTC_TIMESTAMP(6)
WHERE code IN ('ADMIN', 'MANAGER', 'USER');
COMMIT;

-- 이전이 끝난 뒤 현재 코드 ROOT에만 보호 제약을 적용한다.
ALTER TABLE tbl_user
    DROP CHECK ck_root_active,
    MODIFY COLUMN root_guard TINYINT GENERATED ALWAYS AS (CASE WHEN role_code = 'ROOT' THEN 1 ELSE NULL END) STORED,
    ADD CONSTRAINT ck_root_active CHECK (role_code <> 'ROOT' OR (status = 'ACTIVE' AND deleted_at IS NULL));
ALTER TABLE tbl_access_policy_exemption
    DROP CHECK ck_access_exemption_not_root,
    ADD CONSTRAINT ck_access_exemption_not_root CHECK (role_code <> 'ROOT');
