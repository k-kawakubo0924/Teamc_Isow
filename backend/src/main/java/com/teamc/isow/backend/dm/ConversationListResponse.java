package com.teamc.isow.backend.dm;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DM一覧（1ページ分。design/DMlist.png）。
 *
 * @param conversations 最終メッセージの新しい順（メッセージがなければ申込日時）の会話
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（上限で丸めた後の値）
 * @param hasNext 次のページがあるか
 */
public record ConversationListResponse(List<Item> conversations, int page, int size, boolean hasNext) {

    /**
     * 一覧の1件。
     *
     * @param conversationId 会話のID
     * @param status 会話の状態（「終了」などのラベルに使う）
     * @param requestedByMe ログイン中のユーザーが申し込んだ会話か（「メッセージリクエスト」と「送信したリクエスト」の出し分けに使う）
     * @param partner 相手のユーザー
     * @param lastMessageBody 最新メッセージの本文。メッセージがない場合、画像だけのメッセージの場合は null
     * @param lastMessageHasImage 最新メッセージに画像があるか（本文がない場合に「画像」などと表示するため）
     * @param lastMessageAt 最新メッセージの送信日時。メッセージがなければ null。「3分前」などの表示は画面側で作る
     * @param unreadCount 相手から届いた未読メッセージの件数
     * @param requestedAt 申し込んだ日時（メッセージのない申請に「3分前」などを表示するため）
     * @param latestUnread 相手から届いた未読メッセージのうち最新のもの（ホームの新着メッセージ。自分が送ったものは含まない）。
     *     unread=true を指定したときだけ入れ、それ以外は null
     */
    public record Item(
            Long conversationId,
            ConversationStatus status,
            boolean requestedByMe,
            Partner partner,
            String lastMessageBody,
            boolean lastMessageHasImage,
            LocalDateTime lastMessageAt,
            long unreadCount,
            LocalDateTime requestedAt,
            UnreadMessage latestUnread) {
    }

    /**
     * 相手から届いた未読メッセージ。
     *
     * @param body 本文。画像だけのメッセージは null
     * @param hasImage 画像があるか（本文がない場合に「画像」などと表示するため）
     * @param sentAt 送信日時
     */
    public record UnreadMessage(String body, boolean hasImage, LocalDateTime sentAt) {
    }

    /**
     * 相手のユーザー。メールアドレスなどの個人情報は返さない。
     *
     * @param profileImageUrl プロフィール画像の URL。未設定は null
     */
    public record Partner(Long id, String username, String displayName, String profileImageUrl) {
    }
}
