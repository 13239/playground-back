package com.project.playground.domain.gameinvite;

import com.project.playground.global.security.session.Session;
import java.util.Optional;

/** 게임별 방 권한·입장 가능 여부만 분리한다. 새 게임은 공통 만료·응답·방송을 복제하지 않는다. */
public interface GameInvitationAdapter {
    String game();
    GameInvitationDtos.Room requireHost(Session session, String roomId);
    Optional<GameInvitationDtos.Room> available(String roomId, Long inviterId, String inviterSession);
    boolean targetAvailable(Long targetId);
}
