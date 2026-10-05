package com.teamc.isow.backend.dm;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 会話の承認・拒否・終了（docs/dm.md「会話の状態」）。認証が必要（SecurityConfig の anyRequest） */
@RestController
@RequestMapping("/api/conversations/{conversationId:\\d+}")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /** 申請を承認する（申請中 → 進行中） */
    @PostMapping("/accept")
    public ConversationResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return conversationService.accept(jwt.getSubject(), conversationId);
    }

    /** 申請を拒否する（申請中 → 拒否） */
    @PostMapping("/reject")
    public ConversationResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return conversationService.reject(jwt.getSubject(), conversationId);
    }

    /** 会話を終了する（進行中 → 終了） */
    @PostMapping("/end")
    public ConversationResponse end(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return conversationService.end(jwt.getSubject(), conversationId);
    }
}
