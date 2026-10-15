package com.teamc.isow.backend.moderation;

import java.time.LocalDateTime;
import java.util.List;

/** 措置の API の応答（docs/admin.md「6. 措置」） */
public final class ModerationResponses {

    private ModerationResponses() {
    }

    /** 措置の履歴の1行（新しい順） */
    public record HistoryItem(Long id, ModerationActionType action, String reason, String reasonLabel,
            LocalDateTime suspendedUntil, Long reportCaseId, String adminUsername, String memo,
            LocalDateTime createdAt) {
    }

    /** 投稿のいまの状態と措置の履歴 */
    public record PostState(Long postId, String title, Long authorId, String authorUsername, LocalDateTime createdAt,
            boolean hidden, LocalDateTime hiddenAt, String hiddenReason, String hiddenReasonLabel,
            List<HistoryItem> history) {
    }

    /** ユーザーのいまの状態と措置の履歴。suspended は期限を過ぎていれば false */
    public record UserState(Long userId, String username, boolean admin, boolean suspended, LocalDateTime suspendedAt,
            LocalDateTime suspendedUntil, String suspensionReason, String suspensionReasonLabel,
            List<HistoryItem> history) {
    }

    /** 非公開の投稿の一覧の1行 */
    public record HiddenPostItem(Long postId, String title, String authorUsername, LocalDateTime hiddenAt,
            String hiddenReason, String hiddenReasonLabel) {
    }

    public record HiddenPostList(List<HiddenPostItem> items, int page, int size, boolean hasNext) {
    }

    /** 利用停止中のユーザーの一覧の1行 */
    public record SuspendedUserItem(Long userId, String username, LocalDateTime suspendedAt,
            LocalDateTime suspendedUntil, String suspensionReason, String suspensionReasonLabel) {
    }

    public record SuspendedUserList(List<SuspendedUserItem> items, int page, int size, boolean hasNext) {
    }
}
