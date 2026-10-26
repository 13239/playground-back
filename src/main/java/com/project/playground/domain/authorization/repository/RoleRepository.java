package com.project.playground.domain.authorization.repository;

import com.project.playground.domain.authorization.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 권한은 요청마다 최신 DB에서 읽고 편집 트랜잭션만 공통 잠금으로 직렬화한다. */
public interface RoleRepository extends JpaRepository<Role, String> {
    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsByCode(String code);

    // 삭제할 수 없는 ROOT 역할 행을 잠가 초기 배정·권한·역할 변경의 순서를 통일한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.code = 'ROOT'")
    Optional<Role> lockAuthorizationPolicy();

    // 로그인끼리는 공유 잠금을 사용하고 정책·회원 역할 변경과만 발급 순서를 맞춘다.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from Role r where r.code = 'ROOT'")
    Optional<Role> lockAuthorizationPolicyForAdmission();

    // 역할 수/회원 수의 전체 count 없이 검색 가능한 코드 커서 목록을 반환한다.
    @Query("select r from Role r where (:cursor is null or r.code > :cursor) "
            + "and (:search is null or lower(r.code) like :search escape '!' "
            + "or lower(r.name) like :search escape '!') order by r.code")
    Slice<Role> search(@Param("search") String search, @Param("cursor") String cursor, Pageable pageable);

    // ASCII 코드 컬럼에 한글 LIKE를 전달하지 않아 이름 검색의 MySQL 문자셋 충돌을 피한다.
    @Query("select r from Role r where (:cursor is null or r.code > :cursor) "
            + "and lower(r.name) like :search escape '!' order by r.code")
    Slice<Role> searchName(@Param("search") String search, @Param("cursor") String cursor, Pageable pageable);

    // 번호 그리드는 DB 표시 순서와 고유 코드로 정렬해 ROOT·관리자·매니저·유저 순서를 유지한다.
    @Query("select r from Role r where (:search is null or lower(r.code) like :search escape '!' "
            + "or lower(r.name) like :search escape '!') order by r.sortOrder, r.code")
    Page<Role> searchPage(@Param("search") String search, Pageable pageable);

    // 한글 검색의 집계에도 UTF-8 이름만 사용하며 기능 컬렉션은 조회하지 않는다.
    @Query("select r from Role r where lower(r.name) like :search escape '!' order by r.sortOrder, r.code")
    Page<Role> searchNamePage(@Param("search") String search, Pageable pageable);
}
