-- 거절 횟수는 요청/친구 상태가 바뀌어도 보존하며 발신자→수신자 방향별로 구분한다.
CREATE TABLE tbl_social_friend_request_guard (
    requester_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    consecutive_rejections TINYINT UNSIGNED NOT NULL DEFAULT 0,
    blocked_until DATETIME(6) NULL,
    PRIMARY KEY (requester_id, target_id),
    KEY idx_social_friend_request_guard_target (target_id),
    CONSTRAINT fk_social_request_guard_requester FOREIGN KEY (requester_id) REFERENCES tbl_user(id),
    CONSTRAINT fk_social_request_guard_target FOREIGN KEY (target_id) REFERENCES tbl_user(id),
    CONSTRAINT ck_social_request_guard_pair CHECK (requester_id <> target_id),
    CONSTRAINT ck_social_request_guard_rejections CHECK (consecutive_rejections <= 5),
    CONSTRAINT ck_social_request_guard_cooldown CHECK (
        (consecutive_rejections < 5 AND blocked_until IS NULL)
        OR (consecutive_rejections = 5 AND blocked_until IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 데이터는 마지막 거절 한 건만 확인 가능하다. 이를 1회로 이관하고 즉시 재요청을 허용한다.
INSERT INTO tbl_social_friend_request_guard (requester_id, target_id, consecutive_rejections)
SELECT requester_id, IF(first_user_id = requester_id, second_user_id, first_user_id), 1
FROM tbl_social_friend
WHERE state = 'REJECTED';
