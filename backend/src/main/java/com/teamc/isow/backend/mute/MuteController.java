package com.teamc.isow.backend.mute;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」）。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest）。
 * DM の「通知オフ」（/api/conversations/{id}/mute）とは別のもの
 */
@RestController
public class MuteController {

    private final MuteService muteService;

    public MuteController(MuteService muteService) {
        this.muteService = muteService;
    }

    /** ミュートする（204）。すでにミュートしていても 204 */
    @PostMapping("/api/users/{userId:\\d+}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mute(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        muteService.mute(jwt.getSubject(), userId);
    }

    /** ミュートを解除する（204）。ミュートしていなくても 204 */
    @DeleteMapping("/api/users/{userId:\\d+}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmute(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        muteService.unmute(jwt.getSubject(), userId);
    }

    /** 自分がミュートしている人の一覧（ミュートした新しい順） */
    @GetMapping("/api/mutes")
    public MuteListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return muteService.list(jwt.getSubject(), page, size);
    }
}
