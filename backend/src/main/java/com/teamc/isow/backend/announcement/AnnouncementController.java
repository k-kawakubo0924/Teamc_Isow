package com.teamc.isow.backend.announcement;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 利用者側のお知らせ（docs/notification.md「お知らせ」）。通知一覧の「お知らせ」タブで使う。
 * ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest）。
 * 発行は管理者向けの API（/api/admin/announcements）で行う
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementQueryService announcementQueryService;

    public AnnouncementController(AnnouncementQueryService announcementQueryService) {
        this.announcementQueryService = announcementQueryService;
    }

    /** お知らせの一覧（発行日時の新しい順）。本文も含む */
    @GetMapping
    public AnnouncementListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return announcementQueryService.list(jwt.getSubject(), page, size);
    }

    /** 既読にする。id は数字のみ（それ以外は URL が一致せず 404） */
    @PostMapping("/{id:\\d+}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        announcementQueryService.markRead(jwt.getSubject(), id);
    }
}
