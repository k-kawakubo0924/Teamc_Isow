package com.teamc.isow.backend.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * お知らせの発行・一覧の API（docs/admin.md「お知らせの発行」）。/api/admin/** は SecurityConfig で管理者だけに許可している。
 * 利用者側でお知らせを見る API は、まだない（表示場所が未確定のため）
 */
@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {

    private final AdminAnnouncementService announcementService;

    public AdminAnnouncementController(AdminAnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAnnouncementResponse publish(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminAnnouncementRequest request) {
        return announcementService.publish(jwt.getSubject(), request);
    }

    /** 発行済みのお知らせの一覧（新しい順）。size は 1〜50 に丸める */
    @GetMapping
    public AdminAnnouncementResponse.ListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return announcementService.list(jwt.getSubject(), page, size);
    }
}
