package com.project.playground.global.security.jwt;

import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.global.security.session.RedisSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final RedisSessionRepository redisSessionRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId;
        UUID sessionId;

        try {
            userId = Long.valueOf(Objects.requireNonNull(jwt.getSubject()));
            sessionId = UUID.fromString(Objects.requireNonNull(jwt.getClaimAsString("sid")));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("유효하지 않은 토큰입니다.");
        }

        // Redis에서 실제 로그인 중인 세션인지 확인한다.
        // 요청마다 조회하여 변경된 세션 정보와 계정 차단·세션 폐기를 다음 인증에 반영한다.
        RedisSession session = redisSessionRepository.findBySessionId(sessionId, userId)
                .orElseThrow(() -> new InvalidBearerTokenException("종료되었거나 만료된 로그인 세션입니다."));

        // JWT 사용자와 Redis 세션 소유자가 동일해야 한다.
        if (!session.getId().equals(userId)) {
            throw new InvalidBearerTokenException("토큰과 로그인 세션 정보가 일치하지 않습니다.");
        }

        // ROLE 기반 권한을 사용하지 않으며 Bearer 인증 수단만 부여한다.
        List<FactorGrantedAuthority> authorities = List.of(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.BEARER_AUTHORITY));

        // 인증 Principal에는 실제 Redis 세션에서 복원한 Session을 설정한다.
        return new JwtAuthenticationToken(jwt, session.toSession(), authorities);
    }
}
