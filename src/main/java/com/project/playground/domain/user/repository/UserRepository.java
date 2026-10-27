package com.project.playground.domain.user.repository;

import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByIdAndStatusAndDeletedAtIsNull(Long id, UserStatus status);

    boolean existsByUsername(String username);

    /** 삭제된 회원은 관리자 이력에서도 별도 복구·삭제 기능 없이 조회 대상에서 제외한다. */
    boolean existsByIdAndDeletedAtIsNull(Long id);

    // 삭제된 회원의 FK도 남아 있으므로 역할 사용 여부는 전체 회원을 대상으로 확인한다.
    boolean existsByRoleCode(String roleCode);

    Optional<User> findByRoleCode(String roleCode);

    /** 관리자 권한 확인에는 비밀번호나 선택 개인정보를 읽지 않는다. */
    @Query("select u.id as userId, u.roleCode as roleCode, u.roleVersion as roleVersion from User u "
            + "where u.id = :userId and u.status = com.project.playground.domain.user.enums.UserStatus.ACTIVE "
            + "and u.deletedAt is null")
    Optional<UserRoleView> findActiveRole(@Param("userId") Long userId);

    /** 비활성 회원도 역할을 조회/정리할 수 있게 하되 탈퇴·삭제 회원과 인증 정보는 반환하지 않는다. */
    @Query("select u.id as userId, u.roleCode as roleCode, u.roleVersion as roleVersion from User u "
            + "where u.id = :userId and u.deletedAt is null "
            + "and u.status <> com.project.playground.domain.user.enums.UserStatus.WITHDRAWN")
    Optional<UserRoleView> findRoleForManagement(@Param("userId") Long userId);

    /** 최고 관리자 초기 배정과 계정 상태 변경이 같은 회원 행 잠금을 공유한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findForUpdateByUsername(@Param("username") String username);

    /**
     * 계정 상태 변경 시 동일 회원의 비활성화·삭제·재활성화를 직렬화한다.
     * 프로필 변경도 이 잠금을 공유하여 다른 변경이 계정 상태를 덮어쓰지 않도록 한다.
     * 트랜잭션 종료 시까지 데이터베이스 행 잠금을 유지한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findForUpdateById(@Param("userId") Long userId);

    /** 비밀번호 인증 이후 계정 비활성화·역할 변경이 새 세션 발급을 추월하지 못하게 한다. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findForShareById(@Param("userId") Long userId);
}
