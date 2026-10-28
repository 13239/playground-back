-- 기존 회원·로그인 정책에 영향을 주지 않고 신규 가입 제한은 기본적으로 해제한다.
ALTER TABLE tbl_access_policy
    ADD COLUMN sign_up_restricted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '신규 회원가입 제한';
