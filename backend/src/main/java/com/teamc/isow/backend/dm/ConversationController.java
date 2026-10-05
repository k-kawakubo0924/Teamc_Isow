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

    /**
     * DM一覧。status は requested（申請中）・active（進行中）・ended（終了）・chats（進行中と終了）・
     * received（受け取った申請）・sent（送った申請）。指定しなければ申請中・進行中・終了のすべて（拒否は含めない）。
     * q を指定すると、相手のユーザー名・表示名の一部で絞り込む（画面上部の検索欄）。
     * unread=true なら、相手から届いた未読メッセージがある会話だけ（ホームの新着メッセージ）
     */
    @GetMapping
    public ConversationListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ConversationFilter filter = ConversationFilter.fromParam(status)
                .orElseThrow(() -> InputValidationException.of("status", "絞り込みの指定が正しくありません"));
        // ユーザー名・表示名の長さ（50文字）を超える検索語は、一致しないため受け付けない（フォロー一覧と同じ）
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw InputValidationException.of("q", "検索する文字は" + MAX_QUERY_LENGTH + "文字以内で入力してください");
        }
        return conversationListService.list(jwt.getSubject(), filter, q, unread, page, size);
    }

    /** DM の件数（下部ナビの DM のバッジと、DM一覧の「メッセージリクエスト」「送信したリクエスト」の件数） */
    @GetMapping("/summary")
    public ConversationSummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return conversationListService.summary(jwt.getSubject());
    }

    /** 会話1件（チャット画面の上部に相手を表示するため） */
    @GetMapping("/{conversationId:\\d+}")
    public ConversationDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return conversationListService.get(jwt.getSubject(), conversationId);
    }

    private static final int MAX_QUERY_LENGTH = 50;

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
     * 会話のメッセージ（古い順）。どちらも指定しなければ最新の size 件。
     * before を指定するとそのメッセージより古い size 件（上にスクロールしたとき）、
     * after を指定するとそのメッセージより新しい size 件（画面を開いている間の取り直し）。before と after は同時に指定できない。
     * 相手から届いた未読メッセージは既読にする
     */
    @GetMapping("/{conversationId:\\d+}/messages")
    public MessageListResponse messages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Long after,
            @RequestParam(defaultValue = "30") int size) {
        if (before != null && after != null) {
            throw InputValidationException.of("after", "before と after は同時に指定できません");
        }
        return after != null
                ? messageService.listAfter(jwt.getSubject(), conversationId, after, size)
                : messageService.list(jwt.getSubject(), conversationId, before, size);
    }
}
