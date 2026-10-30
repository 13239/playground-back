package com.project.playground.auth.audit;

import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Spring의 동일한 디코더로 저장 형식을 검사한다. 손상된 해시를 비밀번호 오입력으로 분류하지 않는다. */
public final class LoginAuditPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate;

    public LoginAuditPasswordEncoder(PasswordEncoder delegate) { this.delegate = delegate; }
    @Override public String encode(CharSequence rawPassword) { return delegate.encode(rawPassword); }
    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (LoginAuditContext.current() == null) return delegate.matches(rawPassword, encodedPassword);
        try {
            // Argon2 matches()는 손상된 인코딩도 false로 반환하므로 먼저 같은 라이브러리의 파서를 통과시킨다.
            delegate.upgradeEncoding(encodedPassword);
            return delegate.matches(rawPassword, encodedPassword);
        } catch (RuntimeException failure) {
            LoginAuditContext.reason("PASSWORD_HASH_UNREADABLE");
            LoginAuditContext.exception(failure);
            // 라이브러리 예외 메시지에는 저장 해시가 들어갈 수 있어 원인 객체를 외부 예외에 연결하지 않는다.
            throw new InternalAuthenticationServiceException("Stored password verification failed");
        }
    }
    @Override public boolean upgradeEncoding(String encodedPassword) { return delegate.upgradeEncoding(encodedPassword); }
}
