package com.teamc.isow.backend.notification;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // ---- 通知一覧の API（NotificationQueryService）。他人の通知を扱わないよう、すべて受け取る人を条件にする ----

    /**
     * 自分の通知を、通知日時の新しい順（同じなら ID の大きい順）に返す。関連するユーザーも一緒に読み込む
     * （1件ごとに SQL を発行しないため）。全件数を数えず、次のページの有無だけを判定する（Slice）
     */
    @Query(value = """
            SELECT n FROM Notification n JOIN FETCH n.actor
            WHERE n.recipient.id = :recipientId
            ORDER BY n.notifiedAt DESC, n.id DESC
            """)
    Slice<Notification> findPageByRecipientId(@Param("recipientId") Long recipientId, Pageable pageable);

    /** 未読の通知の件数。excludedType の種類は数えない（NotificationType の定数名） */
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipient.id = :recipientId AND n.readAt IS NULL AND n.type <> :excludedType
            """)
    long countUnreadExcludingType(@Param("recipientId") Long recipientId, @Param("excludedType") String excludedType);

    /** 既読にする。すでに既読なら最初に既読にした日時のまま（自分の通知が見つかった件数を返す） */
    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = COALESCE(n.readAt, :now)
            WHERE n.id = :id AND n.recipient.id = :recipientId
            """)
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);

    /** 未読に戻す（自分の通知が見つかった件数を返す） */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = NULL WHERE n.id = :id AND n.recipient.id = :recipientId")
    int markUnread(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** 削除する（削除した件数を返す） */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.recipient.id = :recipientId")
    int deleteOwn(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** 自分の未読の通知をすべて既読にする（既読にした件数を返す） */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipient.id = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);
}
