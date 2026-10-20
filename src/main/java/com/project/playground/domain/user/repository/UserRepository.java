package com.project.playground.domain.user.repository;

import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByIdAndStatusAndDeletedAtIsNull(Long id, UserStatus status);

    boolean existsByUsername(String username);

    /**
     * 계정 상태 변경 시 동일 회원의 비활성화·삭제·재활성화를 직렬화한다.
     * 프로필 변경도 이 잠금을 공유하여 다른 변경이 계정 상태를 덮어쓰지 않도록 한다.
     * 트랜잭션 종료 시까지 데이터베이스 행 잠금을 유지한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findForUpdateById(@Param("userId") Long userId);
}
