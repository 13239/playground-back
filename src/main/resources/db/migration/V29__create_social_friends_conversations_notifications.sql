-- 닉네임·로그인 ID와 분리된 영속 공개 키를 기존 회원에도 부여한다.
ALTER TABLE tbl_user ADD COLUMN public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE tbl_user SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE tbl_user MODIFY public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT (UUID()),
    ADD CONSTRAINT uk_user_public_id UNIQUE (public_id);

-- 정규화한 회원 쌍 하나에 진행 중인 요청/친구 상태 하나만 존재한다.
CREATE TABLE tbl_social_friend (
    first_user_id BIGINT NOT NULL, second_user_id BIGINT NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requester_id BIGINT NOT NULL, state VARCHAR(12) NOT NULL,
    requested_at DATETIME(6) NOT NULL, resolved_at DATETIME(6) NULL,
    PRIMARY KEY (first_user_id, second_user_id), UNIQUE KEY uk_social_request (request_id),
    KEY idx_social_friend_second (second_user_id, state), KEY idx_social_friend_first (first_user_id, state),
    CONSTRAINT fk_social_friend_first FOREIGN KEY (first_user_id) REFERENCES tbl_user(id),
    CONSTRAINT fk_social_friend_second FOREIGN KEY (second_user_id) REFERENCES tbl_user(id),
    CONSTRAINT ck_social_friend_pair CHECK (first_user_id < second_user_id),
    CONSTRAINT ck_social_friend_state CHECK (state IN ('PENDING','ACCEPTED','REJECTED','CANCELLED','REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tbl_social_block (
    user_id BIGINT NOT NULL, target_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
    PRIMARY KEY(user_id,target_id), KEY idx_social_block_target(target_id,user_id),
    CONSTRAINT fk_social_block_user FOREIGN KEY(user_id) REFERENCES tbl_user(id),
    CONSTRAINT fk_social_block_target FOREIGN KEY(target_id) REFERENCES tbl_user(id),
    CONSTRAINT ck_social_block_self CHECK(user_id <> target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tbl_social_room (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kind VARCHAR(8) NOT NULL, name VARCHAR(50) NOT NULL, owner_id BIGINT NOT NULL,
    pair_key VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL, closed_at DATETIME(6) NULL,
    read_until_micros BIGINT NOT NULL DEFAULT 9223372036854775807,
    PRIMARY KEY(id), UNIQUE KEY uk_social_direct_pair(pair_key),
    CONSTRAINT fk_social_room_owner FOREIGN KEY(owner_id) REFERENCES tbl_user(id),
    CONSTRAINT ck_social_room_kind CHECK(kind IN ('DIRECT','GROUP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tbl_social_member (
    room_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL, membership_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    joined_at_micros BIGINT NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY(room_id,user_id), KEY idx_social_member_user(user_id,active),
    CONSTRAINT fk_social_member_room FOREIGN KEY(room_id) REFERENCES tbl_social_room(id),
    CONSTRAINT fk_social_member_user FOREIGN KEY(user_id) REFERENCES tbl_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tbl_social_invitation (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    room_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inviter_id BIGINT NOT NULL, target_id BIGINT NOT NULL, state VARCHAR(12) NOT NULL,
    created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY(id), UNIQUE KEY uk_social_invite_target(room_id,target_id),
    KEY idx_social_invite_inbox(target_id,state,expires_at),
    CONSTRAINT fk_social_invite_room FOREIGN KEY(room_id) REFERENCES tbl_social_room(id),
    CONSTRAINT fk_social_invite_inviter FOREIGN KEY(inviter_id) REFERENCES tbl_user(id),
    CONSTRAINT fk_social_invite_target FOREIGN KEY(target_id) REFERENCES tbl_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tbl_social_notification (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, kind VARCHAR(24) NOT NULL,
    target_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL, read_at DATETIME(6) NULL,
    PRIMARY KEY(id), UNIQUE KEY uk_social_notification_public(public_id),
    KEY idx_social_notification_inbox(user_id,id), KEY idx_social_notification_unread(user_id,read_at),
    CONSTRAINT fk_social_notification_user FOREIGN KEY(user_id) REFERENCES tbl_user(id),
    CONSTRAINT fk_social_notification_actor FOREIGN KEY(actor_id) REFERENCES tbl_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 커밋 뒤 Redis 갱신/알림이 실패해도 원본 상태에서 다시 만들 수 있는 전달 작업이다.
CREATE TABLE tbl_social_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT, room_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_id BIGINT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

