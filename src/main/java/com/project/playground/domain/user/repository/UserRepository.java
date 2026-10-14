package com.project.playground.domain.user.repository;

import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByIdAndStatusAndDeletedAtIsNull(Long id, UserStatus status);

    boolean existsByUsername(String username);
}
