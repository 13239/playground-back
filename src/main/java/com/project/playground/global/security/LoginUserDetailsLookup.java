package com.project.playground.global.security;

import com.project.playground.domain.user.repository.reader.UserReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 로그인 제한과 비밀번호 인증이 같은 HTTP 요청에서 조회한 사용자 정보만 공유한다. */
@Component
@RequestScope
@RequiredArgsConstructor
public class LoginUserDetailsLookup {

    private final UserReader userReader;

    // JPA 엔티티 대신 인증용 스냅샷과 미등록 결과를 보관하며, 요청 종료 시 함께 폐기한다.
    private final Map<String, Optional<CustomUserDetails>> users = new HashMap<>();

    public Optional<CustomUserDetails> findByUsername(String username) {
        // 입력 원문을 키로 사용하고 다음 요청에서는 새로 조회하여 계정 상태를 재확인한다.
        return users.computeIfAbsent(username, name -> userReader.findByUsername(name).map(CustomUserDetails::new));
    }
}
