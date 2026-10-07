package com.teamc.isow.backend.notification;

import java.time.LocalDateTime;

/**
 * 通知のきっかけになった出来事（docs/notification.md）。
 *
 * <p>いいね・フォローなどの処理は、自分のトランザクションの中でこれを ApplicationEventPublisher で出すだけにし、
 * 通知は NotificationService がその処理のコミット後に作る（通知の作成に失敗しても、本来の処理は成功させるため）。
 * 処理が失敗して取り消された場合は、通知も作られない。
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /** 新しくいいねを登録した（すでにいいね済みのときは出さない） */
    public record Liked(Long postId, Long likerId) {
    }

    /** 新しくフォローの行を作った（解除から5分以内の再フォローで行を戻したときは出さない。docs/profile.md） */
    public record Followed(Long followeeId, Long followerId) {
    }

    /** 相談を申し込んだ */
    public record ConsultationRequested(Long conversationId) {
    }

    /** 相談を承認した */
    public record ConsultationApproved(Long conversationId) {
    }

    /** 相談を拒否した */
    public record ConsultationRejected(Long conversationId) {
    }

    /** メッセージを送った（通知は会話ごとに1件にまとめる） */
    public record MessageSent(Long conversationId, Long senderId, LocalDateTime sentAt) {
    }
}
