package com.project.playground.domain.user.service;

import com.project.playground.domain.user.dto.request.UpdateProfileRequest;
import com.project.playground.domain.user.dto.response.UserProfileResponse;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증된 본인 계정만 조회·변경하고 프로필 조회로 Redis 세션 만료를 연장하지 않는다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserProfileService {

    private final UserReader userReader;
    private final UserRepository userRepository;

    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(userReader.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)));
    }

    /** 티켓 예약을 오래 점유하지 않도록 이 경로의 프로필 쿼리에 3초 트랜잭션 예산을 적용한다. */
    @Transactional(readOnly = true, timeout = 3)
    public UserProfileResponse getProfileForChatTicket(Long userId) {
        return getProfile(userId);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        // 계정 비활성화·삭제와 같은 행 잠금을 사용해 오래된 프로필 수정이 계정 상태를 덮어쓰지 않게 한다.
        User user = userRepository.findForUpdateById(userId)
                .filter(value -> value.getDeletedAt() == null && value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeProfileAvatar(request.getAvatarId());
        return UserProfileResponse.from(user);
    }
}
