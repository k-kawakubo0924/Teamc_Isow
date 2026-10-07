package com.teamc.isow.backend.notification;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 受け取る人・種類・会話が同じ通知。type は NotificationType の定数名（findMessageNotification から使う） */
    Optional<Notification> findByRecipientIdAndTypeAndConversationId(Long recipientId, String type, Long conversationId);

    /** 会話のメッセージの通知（会話ごとに1件にまとめるときに、既存の行を探すのに使う） */
    default Optional<Notification> findMessageNotification(Long recipientId, Long conversationId) {
        return findByRecipientIdAndTypeAndConversationId(
                recipientId, NotificationType.MESSAGE_RECEIVED.name(), conversationId);
    }

    /** 投稿・した人・種類が同じ通知があるか。type は NotificationType の定数名（existsLikeNotification から使う） */
    boolean existsByPostIdAndActorIdAndType(Long postId, Long actorId, String type);

    /** 同じ人が同じ投稿にいいねした通知がすでにあるか（再度いいねされたときに、新しく作らないために使う） */
    default boolean existsLikeNotification(Long postId, Long likerId) {
        return existsByPostIdAndActorIdAndType(postId, likerId, NotificationType.LIKED.name());
    }
}
