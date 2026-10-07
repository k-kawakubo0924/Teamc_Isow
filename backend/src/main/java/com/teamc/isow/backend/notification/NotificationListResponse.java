package com.teamc.isow.backend.notification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知一覧（1ページ分。GET /api/notifications）。
 * 文面はサーバーで組み立てず、画面側で type と actor から組み立てる（後から文面を変えやすくするため）。
 *
 * @param notifications 通知日時の新しい順
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（上限で丸めた後の値）
 * @param hasNext 次のページがあるか
 */
public record NotificationListResponse(List<Item> notifications, int page, int size, boolean hasNext) {

    /**
     * 通知の1件。
     *
     * @param type 通知の種類（NotificationType の定数名）
     * @param actor 関連するユーザー（いいね・フォロー・相談・メッセージをした人）
     * @param post 関連する投稿（いいねの通知のみ。それ以外は null）
     * @param conversationId 関連する会話（相談・メッセージの通知のみ。それ以外は null。タップで DM を開くのに使う）
     * @param notifiedAt 通知日時（「〇分前」の表示に使う）
     * @param read 既読か
     */
    public record Item(
            Long id,
            NotificationType type,
            Actor actor,
            PostSummary post,
            Long conversationId,
            LocalDateTime notifiedAt,
            boolean read) {
    }

    /** 関連するユーザー。メールアドレスなどの個人情報は返さない */
    public record Actor(Long id, String username, String profileImageUrl) {
    }

    /** 関連する投稿 */
    public record PostSummary(Long id, String thumbnailUrl) {
    }
}
