-- 최종 확정한 20종으로 교체하며 기존 회원 모두를 시안 5번 새싹 요정으로 한 번만 초기화한다.
-- 관리 버전을 함께 증가시켜 이전 프로필을 읽었던 관리자 편집이 초기화 결과를 덮어쓰지 않게 한다.
UPDATE tbl_user
SET profile_avatar = 'SPROUT',
    management_version = management_version + 1
WHERE profile_avatar <> 'SPROUT';

-- 가입 코드 밖에서 생성되는 회원도 엔티티·프론트엔드와 동일한 기본 캐릭터를 사용한다.
ALTER TABLE tbl_user ALTER COLUMN profile_avatar SET DEFAULT 'SPROUT';
