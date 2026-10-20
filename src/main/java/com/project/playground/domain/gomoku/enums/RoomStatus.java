package com.project.playground.domain.gomoku.enums;

/** 방 생명주기다. 1단계는 생성·닫기만 변경하며 대국 중 상태는 이후 대국 서비스가 관리한다. */
public enum RoomStatus {
    WAITING, PLAYING, CLOSED
}
