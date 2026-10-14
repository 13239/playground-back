package com.project.playground.domain.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum Gender {

    MALE("M", "남성"),
    FEMALE("F", "여성");

    @JsonValue
    @EnumeratedValue
    private final String code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Gender fromCode(String code) {
        return Arrays.stream(values())
                .filter(gender -> gender.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 성별 코드입니다."));
    }
}
