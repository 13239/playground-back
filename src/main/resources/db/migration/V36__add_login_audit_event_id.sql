-- 과거 기록은 그대로 유지하고 로그인 진단 재전송만 고유 이벤트 ID로 중복 방지한다.
ALTER TABLE tbl_activity_log ADD COLUMN login_audit_event_id VARCHAR(36) NULL;
CREATE UNIQUE INDEX uk_activity_log_login_audit_event ON tbl_activity_log (login_audit_event_id);
