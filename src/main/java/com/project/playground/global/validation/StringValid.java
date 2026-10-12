package com.project.playground.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StringValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.TYPE_USE,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface StringValid {

    /**
     * 최대 길이 제한이 없음을 의미하는 내부 기본 max 값.
     * Validator에서도 이 값을 기준으로 길이 검증 생략 여부를 판단한다.
     */
    int UNLIMITED_MAX = Integer.MAX_VALUE;

    /**
     * 검증 실패 시 사용할 고정 메시지.
     * <p>
     * 기본값은 빈 문자열이다.
     * 빈 문자열이면 Validator가 실패 원인에 맞춰 메시지를 자동 생성한다.
     * <p>
     * 자동 생성 메시지 예:
     * required 실패: [이름] 항목을 입력해주세요.
     * type     실패: [이름] 한글만 입력 가능합니다.
     * length   실패: [이름] 허용 길이는 2~6자입니다.
     * <p>
     * 주의: message를 지정하면 실패 원인과 관계없이 이 메시지만 사용된다.
     */
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 사용자에게 보여줄 필드명.
     * 에러 메시지 앞에 [label] 형태로 붙는다.
     * 검증 실패 메시지 예: [이름] 한글만 입력 가능합니다.
     * 필수값이다.
     */
    String label();

    /**
     * 문자열 검증 타입.
     * <p>
     * 생략하면 ANY 타입을 사용한다.
     * ANY는 문자 종류를 제한하지 않는다.
     */
    StringValidType type() default StringValidType.ANY;

    /**
     * 최소 길이.
     * <p>
     * 기본적으로 모든 Unicode code point를 1자로 계산한다.
     * weightedLength = true 이면 ASCII 문자는 1자, 비 ASCII 문자는 2자로 계산한다.
     */
    int min() default 0;

    /**
     * 최대 길이.
     * <p>
     * 기본적으로 모든 Unicode code point를 1자로 계산한다.
     * weightedLength = true 이면 ASCII 문자는 1자, 비 ASCII 문자는 2자로 계산한다.
     */
    int max() default UNLIMITED_MAX;

    /**
     * ASCII 기준 가중 길이 계산 여부.
     * <p>
     * false이면 모든 Unicode code point를 1자로 계산한다.
     * 예: "abc" = 3자, "홍길동" = 3자, "a홍1" = 3자
     * <p>
     * true이면 ASCII 문자는 1자, 비 ASCII 문자는 2자로 계산한다.
     * 예: "abc" = 3자, "홍길동" = 6자, "a홍1" = 4자
     */
    boolean weightedLength() default false;

    /**
     * 필수 입력 여부.
     */
    boolean required() default false;

    /**
     * 일반 공백 허용 여부.
     * <p>
     * KOR, ENG, KOR_ENG 타입에서만 사용할 수 있다.
     * <p>
     * true이면 일반 스페이스 문자(' ')를 허용한다.
     * 탭, 줄바꿈, 캐리지 리턴 및 그 외 유니코드 공백 문자는 허용하지 않는다.
     * <p>
     * false이면 일반 스페이스 문자도 허용하지 않는다.
     * <p>
     * 입력값 전체가 공백만으로 구성된 경우에는
     * allowSpace 설정과 관계없이 유효하지 않다.
     * <p>
     * ANY 타입에는 적용되지 않는다.
     * ANY는 별도의 문자 종류 제한을 수행하지 않는다.
     */
    boolean allowSpace() default false;

    /**
     * ASCII 숫자 0~9 허용 여부.
     */
    boolean allowNumber() default false;

    /**
     * 기본 타입 규칙 외에 추가로 허용할 문자 목록.
     * <p>
     * KOR, ENG, KOR_ENG 타입에서만 사용할 수 있다.
     * <p>
     * 특수문자 전용 옵션이 아니며,
     * 지정한 문자는 기본 타입 규칙과 관계없이 추가로 허용된다.
     * <p>
     * 주의:
     * <ul>
     *     <li>공백 및 제어문자는 지정할 수 없다.</li>
     *     <li>일반 공백은 allowSpace = true로 허용한다.</li>
     *     <li>Unicode code point 단위로 문자를 검사한다.</li>
     *     <li>여러 code point로 구성된 문자는 각각 검사된다.</li>
     * </ul>
     */
    String extraAllowedChars() default "";
}
