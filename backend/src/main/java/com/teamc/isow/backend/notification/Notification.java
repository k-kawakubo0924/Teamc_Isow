package com.teamc.isow.backend.notification;

import com.teamc.isow.backend.dm.Conversation;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.user.User;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 通知（docs/notification.md）。種類ごとの作成用メソッド（liked・followed など）で作る。
 *
 * <p>自分の操作では通知を作らない（自分の投稿に自分でいいね など）。受け取る人と、した人が同じなら作成時にエラーにし、
 * DB の検査制約でも防ぐ。通知を作る処理では、エラーにせず作らずに済ませること。
 *
 * <p>メッセージの通知は会話ごとに1件にまとめる。同じ会話で新しいメッセージが届いたら、新しい行を作らずに
 * messageArrived で通知日時を更新し、未読に戻す（既読にした後に届いた場合も未読になる）。
 *
 * <p>いいねの通知は、同じ人・同じ投稿で1件まで。再度いいねされたときに既存の通知があれば、新しく作らない
 * （既存の通知は日時・既読の状態も変えない。NotificationRepository.existsLikeNotification で確かめる）。
 */
@Entity
@Table(
        name = "notifications",
        // 受け取る人・種類・会話の組み合わせは1件まで（メッセージの通知を会話ごとにまとめるため）。
        // 相談は申し込むたびに新しい会話になるため、相談の通知（届いた・承認・拒否）も会話ごとに1件ずつになる。
        // いいね・フォローは conversation_id が NULL で、NULL どうしは重複とみなされないため、こちらの制約にはかからない
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notifications_recipient_type_conversation",
                        columnNames = {"recipient_id", "type", "conversation_id"}),
                // 同じ人が同じ投稿に再度いいねしても、いいねの通知は1件まで（取り消しと再いいねの連打で通知が溜まらないようにするため）。
                // 投稿を持つのはいいねの通知だけで、それ以外は post_id が NULL のため、この制約にはかからない
                @UniqueConstraint(
                        name = "uk_notifications_post_actor_type",
                        columnNames = {"post_id", "actor_id", "type"})
        },
        indexes = {
                // 自分の通知一覧を新しい順に並べるときに使う
                @Index(name = "idx_notifications_recipient_notified_at", columnList = "recipient_id, notified_at"),
                // 投稿を削除したときの連鎖削除で使う
                @Index(name = "idx_notifications_post_id", columnList = "post_id"),
                @Index(name = "idx_notifications_actor_id", columnList = "actor_id"),
                @Index(name = "idx_notifications_conversation_id", columnList = "conversation_id")
        },
        // 自分の操作では通知を作らない。
        // 種類と関連の組み合わせ（いいねなら投稿が必須など）は、種類を増やしたときに更新されないため検査制約にせず、作成用メソッドで守る
        check = @CheckConstraint(name = "ck_notifications_not_self", constraint = "recipient_id <> actor_id"))
public class Notification {

    /** 通知を一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 通知を受け取る人（本人以外には見せない） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    private User recipient;

    /**
     * 通知の種類。NotificationType の定数名（LIKED など）を文字列で持ち、getType() で enum に戻す
     * （@Enumerated を使わない理由は NotificationType のコメント）
     */
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private String type;

    /** 関連するユーザー（いいね・フォロー・相談の申し込み／承認／拒否・メッセージの送信をした人）。受け取る人とは別の人 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    /** 関連する投稿（いいねの通知のみ。それ以外は null）。投稿を削除すると、この通知も DB が自動で削除する（ON DELETE CASCADE） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    /** 関連する会話（相談・メッセージの通知のみ。それ以外は null） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", updatable = false)
    private Conversation conversation;

    /** 通知を作った日時。メッセージの通知をまとめても変えない */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 最後に通知した日時（一覧の並び順と「〇分前」の表示に使う）。メッセージの通知では、新しいメッセージが届くたびに更新する */
    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    /** 既読にした日時。未読なら null（「未読に戻す」でも null に戻す） */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() {
        // JPA 用
    }

    private Notification(User recipient, NotificationType type, User actor, Post post, Conversation conversation) {
        if (recipient.getId().equals(actor.getId())) {
            throw new IllegalArgumentException("自分の操作では通知を作らない");
        }
        if (type.isWithPost() != (post != null) || type.isWithConversation() != (conversation != null)) {
            throw new IllegalArgumentException(type + " の通知に関連する投稿・会話の組み合わせが正しくない");
        }
        this.recipient = recipient;
        this.type = type.name();
        this.actor = actor;
        this.post = post;
        this.conversation = conversation;
    }

    /** いいねされた。受け取るのは投稿者。同じ人・同じ投稿の通知がすでにあれば作らないこと */
    public static Notification liked(Post post, User liker) {
        return new Notification(post.getAuthor(), NotificationType.LIKED, liker, post, null);
    }

    /** フォローされた。受け取るのはフォローされた人 */
    public static Notification followed(User followee, User follower) {
        return new Notification(followee, NotificationType.FOLLOWED, follower, null, null);
    }

    /** 相談が届いた。受け取るのは申し込まれた人 */
    public static Notification consultationRequested(Conversation conversation) {
        User requester = conversation.getRequestedBy();
        return new Notification(otherParticipant(conversation, requester), NotificationType.CONSULTATION_REQUESTED,
                requester, null, conversation);
    }

    /** 相談が承認された。受け取るのは申し込んだ人 */
    public static Notification consultationApproved(Conversation conversation) {
        User requester = conversation.getRequestedBy();
        return new Notification(requester, NotificationType.CONSULTATION_APPROVED,
                otherParticipant(conversation, requester), null, conversation);
    }

    /** 相談が拒否された。受け取るのは申し込んだ人 */
    public static Notification consultationRejected(Conversation conversation) {
        User requester = conversation.getRequestedBy();
        return new Notification(requester, NotificationType.CONSULTATION_REJECTED,
                otherParticipant(conversation, requester), null, conversation);
    }

    /**
     * メッセージが届いた。受け取るのは送った人の相手。
     * 同じ会話の通知がすでにあれば、新しく作らずに messageArrived を呼ぶこと
     */
    public static Notification messageReceived(Conversation conversation, User sender) {
        return new Notification(otherParticipant(conversation, sender), NotificationType.MESSAGE_RECEIVED,
                sender, null, conversation);
    }

    private static User otherParticipant(Conversation conversation, User user) {
        if (!conversation.isParticipant(user.getId())) {
            throw new IllegalArgumentException("会話の当事者ではない");
        }
        return conversation.getUser1().getId().equals(user.getId()) ? conversation.getUser2() : conversation.getUser1();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.notifiedAt = this.createdAt;
    }

    /**
     * 同じ会話で新しいメッセージが届いた。通知日時を更新し、未読に戻す（既読にした後に届いた場合も未読になる）。
     * メッセージの通知でのみ使う
     */
    public void messageArrived(LocalDateTime arrivedAt) {
        if (getType() != NotificationType.MESSAGE_RECEIVED) {
            throw new IllegalStateException(getType() + " の通知はまとめない");
        }
        this.notifiedAt = arrivedAt;
        this.readAt = null;
    }

    /** 既読にする。すでに既読なら最初に既読にした日時のまま */
    public void markRead(LocalDateTime now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }

    /** 未読に戻す（通知一覧の「未読に戻す」） */
    public void markUnread() {
        this.readAt = null;
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return NotificationType.valueOf(type);
    }

    public User getActor() {
        return actor;
    }

    public Post getPost() {
        return post;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
