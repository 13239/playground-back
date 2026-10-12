package com.project.playground.global.validation;

public enum StringValidType {

    /**
     * 문자 종류는 검증하지 않고 required, blank, length만 검증한다.
     * <p>
     * 사용 예:
     * 제목, 설명, 메모처럼 한글/영문/숫자/기호를 모두 허용하되 길이만 제한하고 싶을 때 사용한다.
     */
    ANY(""),

    KOR("한글만 입력 가능합니다."),

    ENG("영문만 입력 가능합니다."),

    KOR_ENG("한글 또는 영문만 입력 가능합니다."),

    /**
     * 국내 휴대전화, 일반전화, 인터넷전화(070) 형식을 검증한다.
     * <p>
     * 허용 예:
     * 010-1234-5678
     * 011-123-4567
     * 02-123-4567
     * 02-1234-5678
     * 031-123-4567
     * 044-1234-5678
     * 070-1234-5678
     * <p>
     * 주의:
     * 하이픈이 포함된 번호만 허용한다.
     * 국가번호 형식은 허용하지 않는다.
     * 실제 번호의 개통 여부는 검증하지 않는다.
     * 전국대표번호 및 수신자부담번호는 지원하지 않는다.
     */
    PHONE("올바른 연락처 형식이 아닙니다."),

    /**
     * 이메일 형식을 검증한다.
     * <p>
     * 허용 예:
     * test@test.com
     * test.user@test.co.kr
     * user_name+tag@test.com
     * user-name@test.com
     * <p>
     * 실패 예:
     * .test@test.com
     * test.@test.com
     * te..st@test.com
     * test@test
     * test@-test.com
     * test@test-.com
     * <p>
     * 검증 내용:
     * 전체 이메일 길이 최대 254자
     * local part 최대 64자
     * local part의 시작/끝 dot 방지
     * local part의 연속 dot 방지
     * domain label의 시작/끝 hyphen 방지
     * TLD 길이 2~63자
     * <p>
     * 주의:
     * 엄격한 RFC 전체 검증은 아니다.
     * 국제화 이메일 주소는 지원하지 않는다.
     */
    EMAIL("올바른 이메일 형식이 아닙니다."),

    /**
     * 숫자와 하이픈 조합을 검증한다.
     * <p>
     * 주로 사용하는 곳:
     * 계좌번호
     * 숫자와 하이픈만 허용하는 외부 식별번호
     */
    NUMBER_HYPHEN("숫자와 하이픈(-)만 입력 가능하며, 하이픈은 숫자 사이에만 사용할 수 있습니다.");

    private final String message;

    StringValidType(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    /**
     * 문자 허용 옵션을 사용할 수 있는 타입인지 확인한다.
     * <p>
     * 대상 옵션:
     * allowNumber
     * allowSpace
     * extraAllowedChars
     */
    public boolean supportsCharacterPolicyOptions() {
        return switch (this) {
            case KOR, ENG, KOR_ENG -> true;
            case ANY, PHONE, EMAIL, NUMBER_HYPHEN -> false;
        };
    }

    /**
     * ASCII 기준 가중 길이 계산을 사용할 수 있는 타입인지 확인한다.
     * <p>
     * ANY는 문자 종류를 제한하지 않지만, 길이 계산 방식으로 weightedLength를 사용할 수 있다.
     */
    public boolean supportsWeightedLength() {
        return switch (this) {
            case ANY, KOR, ENG, KOR_ENG -> true;
            case PHONE, EMAIL, NUMBER_HYPHEN -> false;
        };
    }
}
