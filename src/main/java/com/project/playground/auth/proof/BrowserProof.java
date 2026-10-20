package com.project.playground.auth.proof;

import lombok.Builder;
import lombok.Getter;

/** 브라우저 증명의 내부 판별 결과다. 서명 검증 또는 명시적 dev HTTP 예외에서만 만들고 외부 JSON으로 받지 않는다. */
@Getter
@Builder
public class BrowserProof {

    public static final String ATTRIBUTE = "playground.browserProof";

    // 실제 공개키 지문과 구분한다. 이 값이 같은 것만으로 두 로그인을 같은 브라우저로 취급하지 않는다.
    public static final String DEV_HTTP_BINDING = "dev-http";

    public static BrowserProof forDevelopmentHttp() {
        return BrowserProof.builder().keyThumbprint(DEV_HTTP_BINDING).scope(DEV_HTTP_BINDING).build();
    }

    private final String keyThumbprint;

    private final String scope;
}
