package com.project.playground.global.security.jwt;

import com.project.playground.domain.user.repository.reader.UserReader;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtAccountStatusValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ACCOUNT = new OAuth2Error("invalid_token", "유효하지 않은 계정입니다.", null);

    private final UserReader userReader;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            Long id = Long.valueOf(jwt.getSubject());

            if (userReader.findActiveById(id).isPresent()) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
        }

        return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
    }
}
