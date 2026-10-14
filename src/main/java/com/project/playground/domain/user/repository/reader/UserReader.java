package com.project.playground.domain.user.repository.reader;

import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserReader {

    private final UserRepository userRepository;

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsernameAndDeletedAtIsNull(username);
    }

    public Optional<User> findActiveById(Long id) {
        return userRepository.findByIdAndStatusAndDeletedAtIsNull(id, UserStatus.ACTIVE);
    }
}
