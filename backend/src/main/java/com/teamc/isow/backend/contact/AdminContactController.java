package com.teamc.isow.backend.contact;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理者のお問い合わせの API（docs/admin.md「7. お問い合わせ」）。/api/admin/** は SecurityConfig で管理者だけに許可し、
 * サービスでも確かめる
 */
@RestController
@RequestMapping("/api/admin/contacts")
public class AdminContactController {

    private final AdminContactService contactService;

    public AdminContactController(AdminContactService contactService) {
        this.contactService = contactService;
    }

    /** 一覧（新しい順）。status・category・channel で絞れる。size は 1〜50 に丸める */
    @GetMapping
    public AdminContactResponses.ListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return contactService.list(jwt.getSubject(), status, category, channel, page, size);
    }

    /** 詳細（内容・別の連絡先）。見たことはログに残さない（AdminContactService） */
    @GetMapping("/{id:\\d+}")
    public AdminContactResponses.Detail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return contactService.detail(jwt.getSubject(), id);
    }

    /** 状態を変える（対応済み・対応不要・未対応に戻す） */
    @PostMapping("/{id:\\d+}/status")
    public AdminContactResponses.StatusResult changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestBody AdminContactStatusRequest request) {
        return contactService.changeStatus(jwt.getSubject(), id, request);
    }
}
