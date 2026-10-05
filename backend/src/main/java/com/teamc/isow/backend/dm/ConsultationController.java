package com.teamc.isow.backend.dm;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 相談の申込と、申し込めるかの確認（docs/dm.md）。認証が必要（SecurityConfig の anyRequest） */
@RestController
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    /** 相談を申し込む。会話を「申請中」で作るため 201 を返す */
    @PostMapping("/api/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsultationRequest request) {
        return consultationService.request(jwt.getSubject(), request);
    }

    /** 相手に相談を申し込めるか（プロフィール画面の「相談する」ボタンの状態に使う） */
    @GetMapping("/api/users/{userId:\\d+}/consultation-status")
    public ConsultationStatusResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return consultationService.status(jwt.getSubject(), userId);
    }
}
