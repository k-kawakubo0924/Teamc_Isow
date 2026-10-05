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
import java.time.LocalDateTime;

/**
 * DM のメッセージ（docs/dm.md「チャット画面」）。
 *
 * <p>申込時の一言メッセージも、申請中の会話の最初のメッセージとしてこのテーブルに保存する。
 * 送信者が会話の参加者であることは DB では検査できないため、保存する側（サービス）で確認する。
 * 保存したら Conversation.recordMessage() で会話の最終メッセージ日時を進めること。
 */
@Entity
@Table(
        name = "messages",
        // チャット画面の表示（会話ごとに送信日時順）と未読数の集計で使う
        indexes = @Index(name = "idx_messages_conversation_id_sent_at", columnList = "conversation_id, sent_at"),
        // 本文だけ・画像だけのメッセージがあるため、どちらか一方は必須とする
        check = @CheckConstraint(name = "ck_messages_body_or_image",
                constraint = "body IS NOT NULL OR image_url IS NOT NULL"))
public class Message {

    /** メッセージを一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 属する会話 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    /** 送信者（会話の参加者のどちらか） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    /** 本文（画像だけのメッセージは null）。文字数の上限は仕様で未確定のため仮の値 */
    @Column(length = 1000)
    private String body;

    /** 画像の保存先 URL（任意。画像がなければ null）。画像そのものは DB に保存しない */
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    /** 送信日時。チャット画面の送信時刻の表示と並び順に使う */
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    /** 相手（送信者でない方）が読んだ日時。未読は null。DM一覧の未読数の集計に使う */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Message() {
        // JPA 用
    }

    public Message(Conversation conversation, User sender, String body, String imageUrl) {
        if (body == null && imageUrl == null) {
            throw new IllegalArgumentException("本文か画像のどちらかが必要です");
        }
        this.conversation = conversation;
        this.sender = sender;
        this.body = body;
        this.imageUrl = imageUrl;
    }

    @PrePersist
    void onCreate() {
        this.sentAt = LocalDateTime.now();
    }

    /** 相手が読んだことを記録する。既読なら最初に読んだ日時のまま変えない */
    public void markAsRead() {
        if (readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
