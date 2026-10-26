package com.project.playground.domain.game.dto.request;

import com.project.playground.domain.game.support.GameCardLabels;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.util.Set;

/** 표시 정보에는 공개·점검 필드를 두지 않아 GAME_WRITE로 운영 권한을 우회하지 못하게 한다. */
@Getter @Builder @Jacksonized
public class GameMetadataRequest {
    @NotBlank @Size(max = 100) private final String name;
    @NotBlank @Size(max = 160) private final String summary;
    @NotBlank @Size(max = 2000) private final String description;
    // 이미지 선택값은 제거하고 장르 연결만 제한된 코드 집합으로 받는다.
    @NotNull @Size(max = 100) private final Set<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String> genreCodes;
    // 빈 집합도 명시적으로 받아 전체 해제와 필드 누락을 구분하고 임의 라벨 문자열은 저장하지 않는다.
    @NotNull @Size(max = GameCardLabels.MAX_LABELS)
    private final Set<@NotNull @Pattern(regexp = GameCardLabels.CODE_PATTERN) String> cardLabelCodes;
    // 노출 순서는 별도의 그리드 순서 API에서만 변경한다.
}
