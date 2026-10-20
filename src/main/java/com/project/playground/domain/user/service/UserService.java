package com.project.playground.domain.user.service;

import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.user.dto.request.SignUpRequest;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserReader userReader;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final RedisSessionRepository redisSessionRepository;

    @Transactional
    public void signUp(SignUpRequest request) {
        if (userReader.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        User user = User.builder()
                .nickname(request.getNickname())
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

    /**
     * 회원 비활성화: 현재 로그인한 모든 기기의 세션을 즉시 무효화한다.
     * <p>
     * 동일 회원의 계정 변경은 DB 행 잠금으로 직렬화한다.
     * Redis 차단 및 세션 폐기를 DB 변경보다 먼저 수행하여 진행 중인 로그인 요청이 신규 세션을 만들 수 없게 한다.
     * <p>
     * Redis가 실패하면 DB 변경 전에 예외가 발생한다.
     * DB가 이후 롤백되면 Redis 차단은 남는다(보안을 우선한 fail-closed).
     */
    @Transactional
    public void deactivate(Long userId) {
        User user = findForUpdate(userId);

        if (user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        redisSessionRepository.blockAndRevokeAll(userId);
        user.deactivate();
    }

    /**
     * 회원 소프트 삭제: 삭제된 계정의 모든 기존 세션을 폐기하고 동일 계정으로 로그인 또는 JWT 인증을 다시 할 수 없도록 차단한다.
     * <p>
     * 중복 삭제 요청에도 Redis 차단과 세션 정리를 다시 수행한다.
     * 계정 데이터는 물리적으로 삭제하지 않고 deletedAt을 사용한다.
     */
    @Transactional
    public void delete(Long userId) {
        User user = findForUpdate(userId);

        redisSessionRepository.blockAndRevokeAll(userId);

        if (user.getDeletedAt() == null) {
            user.delete();
        }

        user.deactivate();
    }

    /**
     * 비활성 또는 가입대기 계정을 재활성화한다.
     * 삭제된 계정은 이 메서드로 복구할 수 없다.
     * <p>
     * 1. 회원 행에 쓰기 잠금을 건다.
     * 2. Redis에 남은 세션을 다시 폐기하고 이번 상태 변경의 차단 표식을 저장한다.
     * 3. MySQL에 ACTIVE를 반영하고 SQL을 flush한다.
     * 4. DB 커밋 이후 차단 표식이 일치할 때만 차단 Key를 해제한다.
     * <p>
     * DB 커밋 전에는 차단을 유지하므로 이전에 비밀번호 인증을 끝낸 로그인 요청도 세션을 만들 수 없다.
     * 커밋 이후 더 최근의 비활성화·삭제가 실행되면 차단 표식이 달라져 이전 재활성화 처리는 차단을 해제하지 못한다.
     * <p>
     * 트랜잭션이 롤백되거나 결과가 불확실해지면 차단 표식을 그대로 유지한다.
     * MySQL·Redis 간 분산 트랜잭션은 아니므로 커밋 후 Redis 해제 실패 시 차단 상태를 확인하고 재활성화를 재시도해야 한다.
     */
    @Transactional
    public void activate(Long userId) {
        User user = findForUpdate(userId);

        if (user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getStatus() == UserStatus.ACTIVE) {
            // DB는 활성 상태이지만 Redis 차단 Key가 남아 있다면 기존 세션을 모두 무효화한 후 차단 상태를 복구한다.
            if (!redisSessionRepository.isAccountBlocked(userId)) {
                return;
            }
        }

        // 이전 토큰 재사용을 방지하기 위해 세션 목록을 다시 비운다.
        String blockToken = redisSessionRepository.blockAndRevokeAll(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            user.activate();
            userRepository.flush();
        }

        // 커밋에 성공한 경우에만 차단 해제를 시도한다. 롤백 시에는 별도 Redis 호출 없이 차단이 유지된다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    redisSessionRepository.unblockAccount(userId, blockToken);
                } catch (RuntimeException e) {
                    // DB 커밋을 되돌릴 수 없으므로 서비스 오류를 반환하고 재시도로 차단 상태를 복구한다.
                    log.error("계정 재활성화 DB 커밋 후 Redis 차단 해제 실패. 재활성화 재시도 필요. userId={}", userId, e);
                    throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
                }
            }
        });
    }

    // 상태 변경 시 동일 사용자 행에 대한 쓰기 잠금을 확보한다.
    private User findForUpdate(Long userId) {
        return userRepository.findForUpdateById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
