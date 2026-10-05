package com.teamc.isow.backend.dm;

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

/**
 * DM の会話（docs/dm.md）。申請中・進行中・終了・拒否を1つのテーブルで管理し、状態で区別する。
 *
 * <p>2人の組み合わせは、ID の小さい方を user1、大きい方を user2 に入れて表す（A-B と B-A を同じ組み合わせにするため）。
 * どちらが申し込んだかは requestedBy で持つ。
 * 拒否・終了した会話は削除せず履歴として残し、再申請では新しい行を作る。
 * 申込時の一言メッセージは、この会話の最初の Message として保存する。
 */
@Entity
@Table(
        name = "conversations",
        // 同じ2人の間で「申請中」または「進行中」の会話は1つまで。ongoing は申請中・進行中なら TRUE、それ以外は NULL で、
        // NULL どうしは重複とみなされないため、拒否・終了の行はいくつあってもよい（Follow.active と同じ方法）。
        // 自分の会話一覧（user1_id で探す）のインデックスも兼ねる
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversations_ongoing", columnNames = {"user1_id", "user2_id", "ongoing"}),
        // 自分の会話一覧（user2_id 側で探す）で使う
        indexes = @Index(name = "idx_conversations_user2_id", columnList = "user2_id"),
        check = {
                // 組み合わせの向きを1通りにする。自分自身との会話もこれで防ぐ
                @CheckConstraint(name = "ck_conversations_user_order", constraint = "user1_id < user2_id"),
                @CheckConstraint(name = "ck_conversations_requested_by",
                        constraint = "requested_by_id = user1_id OR requested_by_id = user2_id"),
                // ddl-auto=update では既存の表に検査制約は追加されないため、この列より前に作った開発用 DB には付かない
                @CheckConstraint(name = "ck_conversations_ended_by",
                        constraint = "ended_by_id IS NULL OR ended_by_id = user1_id OR ended_by_id = user2_id")
        })
public class Conversation {

    /** 会話を一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 参加者のうち ID の小さい方 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user1_id", nullable = false, updatable = false)
    private User user1;

    /** 参加者のうち ID の大きい方 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user2_id", nullable = false, updatable = false)
    private User user2;

    /** 相談を申し込んだ人（user1 か user2 のどちらか）。受けている相談の件数の上限は、相手側の会話だけを数える */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false, updatable = false)
    private User requestedBy;

    /**
     * 状態。ConversationStatus の定数名（REQUESTED など）を文字列で持ち、getStatus() で enum に戻す
     * （@Enumerated を使わない理由は ConversationStatus のコメント）
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** 申請中・進行中なら TRUE、拒否・終了なら NULL（FALSE は使わない）。一意制約のための列で、status と必ず合わせて更新する */
    @Column(name = "ongoing")
    private Boolean ongoing;

    /** 申し込んだ日時 */
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    /** 相手が承認または拒否した日時。申請中は null。拒否後24時間の再申請制限の判定にも使う */
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /** 最後のメッセージの送信日時。メッセージがなければ null。DM一覧の並び順と経過時間の表示に使う */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 会話を終了した日時。終了していなければ null */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * 会話を終了した人（user1 か user2 のどちらか）。終了日時と同時に記録する。終了していなければ null。
     * 通知機能で「誰が終了したか」を使う想定。一か月連絡がない会話の自動終了（未実装）では、終了した人がいないため null のままにする
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ended_by_id")
    private User endedBy;

    protected Conversation() {
        // JPA 用
    }

    /**
     * 申請中の会話を作る。2人とも保存済み（ID がある）こと。
     *
     * @param requester 申し込む人
     * @param recipient 申し込まれる人
     */
    public Conversation(User requester, User recipient) {
        if (requester.getId() == null || recipient.getId() == null) {
            throw new IllegalArgumentException("保存済みのユーザーを指定してください");
        }
        if (requester.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("自分自身には申し込めません");
        }
        boolean requesterFirst = requester.getId() < recipient.getId();
        this.user1 = requesterFirst ? requester : recipient;
        this.user2 = requesterFirst ? recipient : requester;
        this.requestedBy = requester;
        changeStatus(ConversationStatus.REQUESTED);
    }

    @PrePersist
    void onCreate() {
        this.requestedAt = LocalDateTime.now();
    }

    /** 申請を承認する（申請中 → 進行中） */
    public void approve() {
        requireStatus(ConversationStatus.REQUESTED);
        changeStatus(ConversationStatus.ACTIVE);
        this.respondedAt = LocalDateTime.now();
    }

    /** 申請を拒否する（申請中 → 拒否） */
    public void reject() {
        requireStatus(ConversationStatus.REQUESTED);
        changeStatus(ConversationStatus.REJECTED);
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * 会話を終了する（進行中 → 終了）。終了日時と終了した人を同時に記録する
     *
     * @param endedBy 終了した人（この会話の参加者）
     */
    public void end(User endedBy) {
        requireStatus(ConversationStatus.ACTIVE);
        if (!isParticipant(endedBy)) {
            throw new IllegalArgumentException("この会話の参加者ではありません");
        }
        changeStatus(ConversationStatus.ENDED);
        this.endedAt = LocalDateTime.now();
        this.endedBy = endedBy;
    }

    /** メッセージを保存したときに呼び、最終メッセージ日時を進める */
    public void recordMessage(LocalDateTime sentAt) {
        if (lastMessageAt == null || sentAt.isAfter(lastMessageAt)) {
            this.lastMessageAt = sentAt;
        }
    }

    /** 2人のうち、指定したユーザーの相手を返す。参加者でなければ例外 */
    public User otherParticipant(User user) {
        if (user1.getId().equals(user.getId())) {
            return user2;
        }
        if (user2.getId().equals(user.getId())) {
            return user1;
        }
        throw new IllegalArgumentException("この会話の参加者ではありません");
    }

    /** 指定したユーザーがこの会話の参加者か */
    public boolean isParticipant(User user) {
        return isParticipant(user.getId());
    }

    /** 指定した ID のユーザーがこの会話の参加者か */
    public boolean isParticipant(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    /** status と ongoing を必ず一緒に変える */
    private void changeStatus(ConversationStatus newStatus) {
        this.status = newStatus.name();
        this.ongoing = newStatus.isOngoing() ? Boolean.TRUE : null;
    }

    private void requireStatus(ConversationStatus expected) {
        if (getStatus() != expected) {
            throw new IllegalStateException("会話の状態が " + getStatus() + " のため、この操作はできません");
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser1() {
        return user1;
    }

    public User getUser2() {
        return user2;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public ConversationStatus getStatus() {
        return ConversationStatus.valueOf(status);
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public User getEndedBy() {
        return endedBy;
    }
}
