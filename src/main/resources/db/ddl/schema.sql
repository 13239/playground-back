create table `tbl_user` (
    id bigint auto_increment comment 'pk',
    nickname varchar(50) not null comment '닉네임',
    username varchar(50) not null comment '아이디',
    password varchar(255) not null comment '비밀번호',
    email varchar(100) null comment '이메일',
    phone varchar(30) null comment '연락처',
    birthday date null comment '생년월일',
    gender char(1) null comment '성별',
    status varchar(10) not null comment '계정상태',
    created_at datetime not null comment '생성시간',
    updated_at datetime null default null comment '수정시간',
    deleted_at datetime null default null comment '삭제시간',
    primary key (id),
    unique key (username)
) comment = '회원';

