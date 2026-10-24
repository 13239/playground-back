package com.project.playground.domain.user.service;

import com.project.playground.domain.user.dto.request.UpdateProfileRequest;
import com.project.playground.domain.user.dto.request.UpdateNicknameRequest;
import com.project.playground.domain.user.dto.response.UserProfileResponse;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.domain.user.repository.UserNicknameHistoryRepository;
import com.project.playground.domain.user.entity.UserNicknameHistory;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** 인증된 본인 계정만 조회·변경하고 프로필 조회로 Redis 세션 만료를 연장하지 않는다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserProfileService {

    private final UserReader userReader;
    private final UserRepository userRepository;
    private final ChatProfilePublisher chatProfiles;
    private final UserNicknameHistoryRepository nicknameHistory;

    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(userReader.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)));
    }

    /** 티켓 예약을 오래 점유하지 않도록 이 경로의 프로필 쿼리에 3초 트랜잭션 예산을 적용한다. */
    @Transactional(readOnly = true, timeout = 3)
    public UserProfileResponse getProfileForChatTicket(Long userId) {
        // 프로필 변경과 같은 행에서 읽기 잠금을 얻어 Redis 세대와 DB 읽기 순서를 일치시킨다.
        User user = userRepository.findForShareById(userId)
                .filter(value -> value.getDeletedAt() == null && value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserProfileResponse profile = UserProfileResponse.from(user);
        chatProfiles.synchronize(userId, profile);
        return profile;
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        // 계정 비활성화·삭제와 같은 행 잠금을 사용해 오래된 프로필 수정이 계정 상태를 덮어쓰지 않게 한다.
        User user = userRepository.findForUpdateById(userId)
                .filter(value -> value.getDeletedAt() == null && value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeProfileAvatar(request.getAvatarId());
        UserProfileResponse profile = UserProfileResponse.from(user);
        chatProfiles.afterCommit(userId, profile);
        return profile;
    }

    /** 이미지와 닉네임을 따로 수정해 다른 탭의 오래된 이미지 선택이 닉네임을 덮어쓰지 않게 한다. */
    @Transactional
    public UserProfileResponse updateNickname(Long userId, UpdateNicknameRequest request) {
        User user = userRepository.findForUpdateById(userId)
                .filter(value -> value.getDeletedAt() == null && value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 같은 이름 재저장은 이력을 만들거나 채팅 알림 세대를 증가시키지 않는다.
        if (Objects.equals(user.getNickname(), request.getNickname())) return UserProfileResponse.from(user);
        String previous = user.getNickname();
        user.changeNickname(request.getNickname());
        // 회원 행 잠금 안에서 변경과 이력을 함께 저장한다. 이력 저장 실패 시 닉네임 변경도 롤백된다.
        nicknameHistory.saveAndFlush(UserNicknameHistory.builder().userId(userId).actorUserId(userId)
                .previousNickname(previous).newNickname(user.getNickname())
                .changedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul"))).build());
        UserProfileResponse profile = UserProfileResponse.from(user);
        chatProfiles.afterCommit(userId, profile);
        return profile;
    }
}
