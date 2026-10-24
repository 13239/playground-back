-- 기존 활동 부가 정보의 아이디·닉네임을 읽는 가상 컬럼이다. 원본 로그와 기존 저장 절차는 유지한다.
-- 미등록 아이디·중복 가입 실패도 회원 행 조인 없이 인덱스로 검색할 수 있다.
ALTER TABLE tbl_activity_log
    ADD COLUMN search_username VARCHAR(50) GENERATED ALWAYS AS (
        CASE WHEN JSON_TYPE(JSON_EXTRACT(detail, '$.username')) = 'STRING'
            THEN LEFT(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.username')), 50) ELSE NULL END
    ) VIRTUAL,
    ADD COLUMN search_nickname VARCHAR(50) GENERATED ALWAYS AS (
        CASE WHEN JSON_TYPE(JSON_EXTRACT(detail, '$.nickname')) = 'STRING'
            THEN LEFT(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.nickname')), 50) ELSE NULL END
    ) VIRTUAL,
    ADD INDEX idx_activity_search_username (search_username, occurred_at, id),
    ADD INDEX idx_activity_search_nickname (search_nickname, occurred_at, id),
    ADD INDEX idx_activity_result_time (result, occurred_at, id),
    ADD INDEX idx_activity_type_result_time (activity_type, result, occurred_at, id);

-- 날짜/행위자/대상의 기존 인덱스는 유지하고 새 화면의 작업·대상 종류 필터를 보완한다.
ALTER TABLE tbl_admin_audit_log
    ADD INDEX idx_admin_audit_action_time (action, occurred_at, id),
    ADD INDEX idx_admin_audit_type_time (target_type, occurred_at, id);
