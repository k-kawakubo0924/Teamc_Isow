package com.teamc.isow.backend.notification;

/**
 * 通知の種類（docs/notification.md）。
 *
 * <p>DB には定数名を文字列の列として保存する（Notification.type を参照）。
 * {@code @Enumerated} は使わない。Hibernate が列に値の一覧の検査制約を付け、ddl-auto=update では定数を追加しても
 * 制約が更新されず、新しい値を保存できなくなるため（コンバーターを使っても同じ制約が付く）。
 * 順番の数値（ORDINAL）も、並び替え・追加で既存データの意味が変わるため使わない。
 * 定数名は既存データとの対応に使うため変更しないこと。画面の文言を変える場合は画面側だけを変える。
 */
public enum NotificationType {

    /** いいねされた（関連：いいねした人・投稿） */
    LIKED(true, false),
    /** フォローされた（関連：フォローした人） */
    FOLLOWED(false, false),
    /** 相談が届いた（関連：申し込んだ人・会話） */
    CONSULTATION_REQUESTED(false, true),
    /** 相談が承認された（関連：承認した人・会話） */
    CONSULTATION_APPROVED(false, true),
    /** 相談が拒否された（関連：拒否した人・会話） */
    CONSULTATION_REJECTED(false, true),
    /** メッセージが届いた（関連：送った人・会話）。会話ごとに1件にまとめる（Notification.messageArrived） */
    MESSAGE_RECEIVED(false, true);

    /** 関連する投稿を必ず持つか（持たない種類では null） */
    private final boolean withPost;

    /** 関連する会話を必ず持つか（持たない種類では null） */
    private final boolean withConversation;

    NotificationType(boolean withPost, boolean withConversation) {
        this.withPost = withPost;
        this.withConversation = withConversation;
    }

    public boolean isWithPost() {
        return withPost;
    }

    public boolean isWithConversation() {
        return withConversation;
    }
}
