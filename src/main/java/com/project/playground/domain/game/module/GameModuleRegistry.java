package com.project.playground.domain.game.module;

import com.project.playground.domain.game.entity.GameMode;
import com.project.playground.global.exception.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 등록 코드만으로 임의 게임을 실행하지 않으며 배포 구현과 DB 설정의 호환성을 항상 확인한다. */
@Component
public class GameModuleRegistry {
    private final Map<String, GameModule> modules;
    public GameModuleRegistry(List<GameModule> modules) {
        this.modules = modules.stream().collect(Collectors.toUnmodifiableMap(GameModule::code, Function.identity()));
        this.modules.forEach((code, module) -> {
            if (!code.matches("[A-Z][A-Z0-9_]{0,63}") || module.modes().isEmpty()
                    || module.modes().stream().map(GameModeDefinition::getCode).distinct().count() != module.modes().size()) {
                throw new IllegalStateException("게임 구현 등록 정의가 올바르지 않습니다.");
            }
            module.modes().forEach(definition -> {
                if (!definition.getCode().matches("[A-Z][A-Z0-9_]{0,63}") || definition.getMinHumanPlayers() < 1
                        || definition.getMaxHumanPlayers() < definition.getMinHumanPlayers() || definition.getMaxHumanPlayers() > 10000
                        || definition.getVersion() < 1 || definition.getGraphicsType() == null || definition.getConnectionType() == null
                        || definition.getInteractionType() == null) throw new IllegalStateException("게임 모드 지원 정의가 올바르지 않습니다.");
            });
        });
    }
    public GameModule require(String code) {
        var module = modules.get(code);
        if (module == null) throw new BusinessException(ErrorCode.GAME_IMPLEMENTATION_UNAVAILABLE);
        return module;
    }
    public GameModeDefinition requireMode(String gameCode, String modeCode) {
        return require(gameCode).modes().stream().filter(mode -> modeCode.equals(mode.getCode())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_IMPLEMENTATION_UNAVAILABLE));
    }
    public List<GameModule> all() { return modules.values().stream().sorted(Comparator.comparing(GameModule::code)).toList(); }
    /** 지원 모드를 구현 정의 순서로 배치하고 미지원 모드도 관리자 조회에서는 마지막에 보존한다. */
    public List<GameMode> orderedModes(String gameCode, List<GameMode> modes) {
        var order = new HashMap<String, Integer>();
        var module = modules.get(gameCode);
        if (module != null) {
            var definitions = module.modes();
            for (int index = 0; index < definitions.size(); index++) order.put(definitions.get(index).getCode(), index);
        }
        return modes.stream().sorted(Comparator.comparingInt((GameMode mode) -> order.getOrDefault(mode.getCode(), Integer.MAX_VALUE))
                .thenComparing(GameMode::getCode)).toList();
    }
    public boolean supports(String gameCode, GameMode mode) {
        var module = modules.get(gameCode);
        return module != null && module.modes().stream().anyMatch(definition -> definition.getCode().equals(mode.getCode())
                && definition.getVersion() == mode.getDefinitionVersion() && definition.getGraphicsType() == mode.getGraphicsType()
                && definition.getConnectionType() == mode.getConnectionType() && definition.getInteractionType() == mode.getInteractionType()
                && mode.getMinHumanPlayers() >= definition.getMinHumanPlayers() && mode.getMaxHumanPlayers() <= definition.getMaxHumanPlayers()
                && mode.getMinHumanPlayers() <= mode.getMaxHumanPlayers());
    }
    public void validatePlayers(GameModeDefinition definition, int min, int max) {
        if (min > max || min < definition.getMinHumanPlayers() || max > definition.getMaxHumanPlayers()) {
            throw new BusinessException(ErrorCode.GAME_CONFIGURATION_INVALID);
        }
    }
}
