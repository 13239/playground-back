package com.project.playground.domain.tetris.controller;

import com.project.playground.domain.tetris.dto.*;
import com.project.playground.domain.tetris.service.TetrisService;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 기존 BFF·JWT·브라우저 증명 필터를 통과한 계정으로만 시작·기록 조회·결과 제출을 허용한다. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/tetris")
public class TetrisController {
    private final TetrisService tetris;
    @PostMapping("/overview")
    public ResponseEntity<TetrisResponse.Overview> overview(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody TetrisRequest.Overview request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tetris.overview(session.getId(), request.getPage()));
    }
    @PostMapping("/start")
    public ResponseEntity<TetrisResponse.Start> start(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody TetrisRequest.Start request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tetris.start(session, request));
    }
    @PostMapping("/finish")
    public ResponseEntity<TetrisResponse.Overview> finish(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody TetrisRequest.Finish request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tetris.finish(session, request));
    }
}
