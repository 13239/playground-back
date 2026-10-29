package com.project.playground.domain.shooting.controller;

import com.project.playground.domain.shooting.dto.*;
import com.project.playground.domain.shooting.service.ShootingService;
import com.project.playground.domain.shooting.service.ShootingLobbyService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 별도 HMAC 체인만 통과할 수 있는 게임 서버 전용 경계다. 브라우저 API와 경로를 분리한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/shooting")
public class ShootingInternalController {
    private final ShootingService shooting;
    private final ShootingLobbyService lobby;

    /** 이미 연결된 게임 서버도 새로운 도전 직전에 운영 정책을 다시 확인한다. */
    @PostMapping("/restart")
    public ResponseEntity<Void> restart(@Valid @RequestBody ShootingRequest.Ticket request) {
        shooting.restart(request);
        return ResponseEntity.noContent().build();
    }

    /** 대기실 공개 여부는 HMAC을 통과한 게임 서버만 갱신한다. */
    @PostMapping("/lobby")
    public ResponseEntity<Void> lobby(@Valid @RequestBody ShootingLobbyService.Update request) {
        lobby.publish(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/consume")
    public ShootingResponse.Identity consume(@Valid @RequestBody ShootingRequest.Consume request) {
        return shooting.consume(request);
    }

    @PostMapping("/claim")
    public ResponseEntity<Void> claim(@Valid @RequestBody ShootingRequest.Lease request) {
        shooting.claim(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@Valid @RequestBody ShootingRequest.Lease request) {
        shooting.validateLease(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@Valid @RequestBody ShootingRequest.Lease request) {
        shooting.release(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/checkpoint")
    public ResponseEntity<Void> checkpoint(@Valid @RequestBody ShootingRequest.Checkpoint request) {
        shooting.checkpoint(request);
        return ResponseEntity.noContent().build();
    }
}
