-- Flyway 도입 시점의 전체 스키마다. 기존 DB는 이 버전으로 baseline하고 신규 DB에만 이 SQL을 실행한다.
-- 이미 적용한 프로필 컬럼을 포함한다. 배포 후에는 이 파일을 수정하지 않고 V2 이상의 새 SQL을 추가한다.
CREATE TABLE tbl_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'pk',
    nickname VARCHAR(50) NOT NULL COMMENT '닉네임',
    profile_avatar VARCHAR(20) NOT NULL DEFAULT 'CAT' COMMENT '기본 프로필 캐릭터 식별자',
    username VARCHAR(50) NOT NULL COMMENT '아이디',
    password VARCHAR(255) NOT NULL COMMENT '비밀번호',
    email VARCHAR(100) NULL COMMENT '이메일',
    phone VARCHAR(30) NULL COMMENT '연락처',
    birthday DATE NULL COMMENT '생년월일',
    gender CHAR(1) NULL COMMENT '성별',
    status VARCHAR(10) NOT NULL COMMENT '계정상태',
    created_at DATETIME NOT NULL COMMENT '생성시간',
    updated_at DATETIME NULL DEFAULT NULL COMMENT '수정시간',
    deleted_at DATETIME NULL DEFAULT NULL COMMENT '삭제시간',
    PRIMARY KEY (id),
    UNIQUE KEY username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원';
