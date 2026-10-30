package com.project.playground.global.exception;

import org.springframework.http.ProblemDetail;

public final class ApiProblemFactory {

    private ApiProblemFactory() {
    }

    /**
     * HTTP 상태와 안내 메시지를 설정하고, 클라이언트가 오류를 구분할 수 있도록 code를 추가한다.
     */
    public static ProblemDetail create(ErrorCode errorCode) {
        errorCode = com.project.playground.auth.audit.LoginAuditContext.publicError(errorCode);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(),
                errorCode.getMessage()
        );

        // enum 상수 이름을 클라이언트와 공유하는 오류 식별자로 사용한다.
        problem.setProperty("code", errorCode.name());

        return problem;
    }
}
