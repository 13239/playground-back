package com.project.playground.global.security.jwt;

import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.security.CustomUserDetails;
import com.project.playground.global.security.session.RedisSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserReader userReader;

    private final RedisSessionRepository redisSessionRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId;
        UUID sessionId;

        try {
            userId = Long.valueOf(jwt.getSubject());
            sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("유효하지 않은 토큰입니다.");
        }

        // Redis에서 실제 로그인 중인 세션인지 확인한다.
        RedisSession session = redisSessionRepository
                .findBySessionId(sessionId)
                .orElseThrow(() -> new InvalidBearerTokenException("종료되었거나 만료된 로그인 세션입니다."));

        // JWT 사용자와 Redis 세션 소유자가 동일해야 한다.
        if (!session.getId().equals(userId)) {
            throw new InvalidBearerTokenException("토큰과 로그인 세션 정보가 일치하지 않습니다.");
        }

        // 계정이 활성 상태인지 확인한다.
        User user = userReader.findActiveById(userId).orElseThrow(() -> new InvalidBearerTokenException("유효하지 않은 계정입니다."));

        CustomUserDetails userDetails = new CustomUserDetails(user);

        List<GrantedAuthority> authorities = new ArrayList<>(userDetails.getAuthorities());

        authorities.add(
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.BEARER_AUTHORITY)
        );

        // 인증 Principal에는 실제 Redis 세션에서 복원한 Session을 설정한다.
        return new JwtAuthenticationToken(jwt, session.toSession(), authorities);
    }
}
