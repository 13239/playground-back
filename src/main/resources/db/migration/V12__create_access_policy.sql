-- 운영 정책과 영구 세션 폐기 경계를 함께 보관하여 Redis 재시작 후에도 이전 로그인이 살아나지 않게 한다.
CREATE TABLE tbl_access_policy (
    id BIGINT NOT NULL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    message VARCHAR(300) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    changed_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_access_policy_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='전역 접속 제한';

CREATE TABLE tbl_access_policy_exemption (
    policy_id BIGINT NOT NULL,
    role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (policy_id, role_code),
    CONSTRAINT fk_access_exemption_policy FOREIGN KEY (policy_id) REFERENCES tbl_access_policy (id),
    CONSTRAINT fk_access_exemption_role FOREIGN KEY (role_code) REFERENCES tbl_role (code),
    CONSTRAINT ck_access_exemption_not_super CHECK (role_code <> 'SUPER_ADMIN')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='최고 관리자가 지정한 접속 허용 역할';

-- 삭제 후 같은 역할 코드가 다시 생겨도 폐기 경계는 보존하므로 이 열에는 역할 FK를 걸지 않는다.
CREATE TABLE tbl_access_policy_revocation (
    policy_id BIGINT NOT NULL,
    role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revoked_before_version BIGINT NOT NULL,
    PRIMARY KEY (policy_id, role_code),
    CONSTRAINT fk_access_revocation_policy FOREIGN KEY (policy_id) REFERENCES tbl_access_policy (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='역할별 이전 로그인 폐기 경계';

INSERT INTO tbl_access_policy (id, enabled, message, version, changed_at)
VALUES (1, FALSE, '서버 점검 중입니다. 잠시 후 다시 로그인해 주세요.', 0, CURRENT_TIMESTAMP(6));
