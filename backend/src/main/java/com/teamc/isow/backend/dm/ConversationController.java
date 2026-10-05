package com.teamc.isow.backend.dm;

import com.teamc.isow.backend.common.InputValidationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * DM一覧、会話の承認・拒否・終了、メッセージの送信・取得（docs/dm.md）。認証が必要（SecurityConfig の anyRequest）。
 * 相談の申込（POST /api/conversations）は ConsultationController。
 * 会話を指定する操作は、当事者でなければ会話が存在しない場合と同じ 404 にする。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationListService conversationListService;
    private final MessageService messageService;

    public ConversationController(
            ConversationService conversationService,
            ConversationListService conversationListService,
            MessageService messageService) {
        this.conversationService = conversationService;
        this.conversationListService = conversationListService;
        this.messageService = messageService;
    }

    /** DM一覧。status は requested（申請中）・active（進行中）・ended（終了）。指定しなければ3つすべて（拒否は含めない） */
    @GetMapping
    public ConversationListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ConversationFilter filter = ConversationFilter.fromParam(status)
                .orElseThrow(() -> InputValidationException.of("status", "絞り込みの指定が正しくありません"));
        return conversationListService.list(jwt.getSubject(), filter, page, size);
    }

    /** 申請を承認する（申請中 → 進行中） */
    @PostMapping("/{conversationId:\\d+}/accept")
    public ConversationResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return conversationService.accept(jwt.getSubject(), conversationId);
    }

    /** 申請を拒否する（申請中 → 拒否） */
    @PostMapping("/{conversationId:\\d+}/reject")
    public ConversationResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return conversationService.reject(jwt.getSubject(), conversationId);
    }

    /** 会話を終了する（進行中 → 終了） */
    @PostMapping("/{conversationId:\\d+}/end")
    public ConversationResponse end(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return conversationService.end(jwt.getSubject(), conversationId);
    }

    /** メッセージを送信する。本文（body）と画像（image）を multipart/form-data で受け取る。進行中の会話だけ送れる */
    @PostMapping(path = "/{conversationId:\\d+}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @ModelAttribute MessageSendRequest request) {
        return messageService.send(jwt.getSubject(), conversationId, request);
    }

    /**
     * 会話のメッセージ（古い順）。before を指定しなければ最新の size 件、指定すればそのメッセージより古い size 件。
     * 相手から届いた未読メッセージは既読にする
     */
    @GetMapping("/{conversationId:\\d+}/messages")
    public MessageListResponse messages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "30") int size) {
        return messageService.list(jwt.getSubject(), conversationId, before, size);
    }
}
