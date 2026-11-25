package com.teamc.isow.backend.moderation;

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
 * 措置の API（docs/admin.md「6. 措置」）。/api/admin/** は SecurityConfig で管理者だけに許可し、サービスでも確かめる。
 * 管理者は、利用者向けの API（/api/posts/** など）からは他人の投稿を編集・削除できない。措置はここからだけ行う
 */
@RestController
@RequestMapping("/api/admin/moderation")
public class AdminModerationController {

    private final AdminModerationService moderationService;

    public AdminModerationController(AdminModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/posts/{postId:\\d+}")
    public ModerationResponses.PostState post(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId) {
        return moderationService.post(jwt.getSubject(), postId);
    }

    @PostMapping("/posts/{postId:\\d+}/hide")
    public ModerationResponses.PostState hide(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId,
            @RequestBody ModerationRequests.Hide request) {
        return moderationService.hide(jwt.getSubject(), postId, request);
    }

    @PostMapping("/posts/{postId:\\d+}/unhide")
    public ModerationResponses.PostState unhide(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId,
            @RequestBody ModerationRequests.Undo request) {
        return moderationService.unhide(jwt.getSubject(), postId, request);
    }

    @GetMapping("/hidden-posts")
    public ModerationResponses.HiddenPostList hiddenPosts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return moderationService.hiddenPosts(jwt.getSubject(), page, size);
    }

    @GetMapping("/users")
    public ModerationResponses.UserState userByUsername(@AuthenticationPrincipal Jwt jwt,
            @RequestParam String username) {
        return moderationService.userByUsername(jwt.getSubject(), username);
    }

    @GetMapping("/users/{userId:\\d+}")
    public ModerationResponses.UserState user(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return moderationService.user(jwt.getSubject(), userId);
    }

    @PostMapping("/users/{userId:\\d+}/suspend")
    public ModerationResponses.UserState suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
            @RequestBody ModerationRequests.Suspend request) {
        return moderationService.suspend(jwt.getSubject(), userId, request);
    }

    @PostMapping("/users/{userId:\\d+}/unsuspend")
    public ModerationResponses.UserState unsuspend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
            @RequestBody ModerationRequests.Undo request) {
        return moderationService.unsuspend(jwt.getSubject(), userId, request);
    }

    @GetMapping("/suspended-users")
    public ModerationResponses.SuspendedUserList suspendedUsers(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return moderationService.suspendedUsers(jwt.getSubject(), page, size);
    }
}
