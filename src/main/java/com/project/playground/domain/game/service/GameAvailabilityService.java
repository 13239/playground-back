package com.project.playground.domain.game.service;

import com.project.playground.domain.game.dto.response.GameModeResponse;
import com.project.playground.domain.game.enums.GameAvailability;
import com.project.playground.domain.game.module.GameModuleRegistry;
import com.project.playground.domain.game.repository.*;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 표시 캐시를 신뢰하지 않고 신규 실행·입장·재대국 직전에 DB 정책을 공유 잠금으로 확인한다. */
@Service @RequiredArgsConstructor
public class GameAvailabilityService {
    private final GameRepository games;
    private final GameModeRepository modes;
    private final GameModuleRegistry modules;

    /** 초대 표시에는 읽기 전용 정책 조회만 사용한다. 실제 입장은 requireNewPlay에서 다시 잠금 검증한다. */
    @Transactional(readOnly = true)
    public boolean canStart(String gameCode, String modeCode) {
        var game = games.findByCode(gameCode)
                .filter(value -> value.isPublished() && value.getAvailability() == GameAvailability.AVAILABLE)
                .orElse(null);
        return game != null && modes.findByGameIdAndCode(game.getId(), modeCode)
                .filter(value -> value.isPublished() && value.getAvailability() == GameAvailability.AVAILABLE)
                .filter(value -> modules.supports(gameCode, value)).isPresent();
    }

    @Transactional
    public GameModeResponse requireNewPlay(String gameCode, String modeCode) {
        var game = games.findForPlay(gameCode).filter(value -> value.isPublished())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_GAME_NOT_FOUND));
        if (game.getAvailability() != GameAvailability.AVAILABLE) throw new GameUnavailableException(game.getUnavailableMessage());
        var mode = modes.findForPlay(game.getId(), modeCode).filter(value -> value.isPublished())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_MODE_NOT_FOUND));
        if (!modules.supports(gameCode, mode)) throw new BusinessException(ErrorCode.GAME_IMPLEMENTATION_UNAVAILABLE);
        if (mode.getAvailability() != GameAvailability.AVAILABLE) throw new GameUnavailableException(mode.getUnavailableMessage());
        return GameModeResponse.from(mode, true);
    }
}
