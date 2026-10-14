package com.project.playground.domain.user.service;

import com.project.playground.domain.user.dto.request.SignUpRequest;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserReader userReader;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(SignUpRequest request) {
        if (userReader.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        User user = User.builder()
                .name(request.getName())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .phone(request.getPhone())
                .birthday(request.getBirthday())
                .gender(request.getGender())
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
    }
}
