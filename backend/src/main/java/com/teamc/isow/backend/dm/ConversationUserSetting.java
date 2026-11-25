package com.teamc.isow.backend.dm;

import com.teamc.isow.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 相談ごとの、1人分の設定（docs/dm.md「ピン留め」「通知オフ」）。ピン留めと通知オフを1行で持つ。
 *
 * <ul>
 *   <li>当事者の2人は別々の行を持つ。<b>相手の設定は相手への応答に含めず、通知もしない</b>（相手には分からない）</li>
 *   <li>どちらの設定もなくなったら行を消す（ConversationSettingService）</li>
 * </ul>
 */
@Entity
@Table(
        name = "conversation_user_settings",
        // 1人・1つの相談につき1行。自分の設定を探すインデックスも兼ねる
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_user_settings", columnNames = {"user_id", "conversation_id"}))
public class ConversationUserSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    /** ピン留めした日時。していなければ null */
    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    /** 通知をオフにした日時。オンなら null */
    @Column(name = "muted_at")
    private LocalDateTime mutedAt;

    protected ConversationUserSetting() {
        // JPA 用
    }

    ConversationUserSetting(User user, Conversation conversation) {
        this.user = user;
        this.conversation = conversation;
    }

    void pin(LocalDateTime now) {
        if (pinnedAt == null) {
            pinnedAt = now;
        }
    }

    void unpin() {
        pinnedAt = null;
    }

    void mute(LocalDateTime now) {
        if (mutedAt == null) {
            mutedAt = now;
        }
    }

    void unmute() {
        mutedAt = null;
    }

    /** どちらの設定もなければ、行を消してよい */
    boolean isEmpty() {
        return pinnedAt == null && mutedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public boolean isPinned() {
        return pinnedAt != null;
    }

    public boolean isMuted() {
        return mutedAt != null;
    }
}
