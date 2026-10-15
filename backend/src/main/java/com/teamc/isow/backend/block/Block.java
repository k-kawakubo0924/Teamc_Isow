package com.teamc.isow.backend.block;

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
 * ブロック（docs/profile.md「ブロック」）。「blocker が blocked をブロックした」を1行で持つ。
 *
 * <p>効果は両方向：どちらの向きの行があっても、2人はお互いの投稿・プロフィールが見えなくなる（VisibilityConditions・UserVisibility）。
 * 解除できるのは、ブロックした本人（blocker）だけ。解除は行を削除する
 */
@Entity
@Table(
        name = "blocks",
        // 同じ組み合わせは1行まで。ブロック中の一覧（blocker_id で探す）・「見えない相手」の判定のインデックスも兼ねる
        uniqueConstraints = @UniqueConstraint(name = "uk_blocks_blocker_blocked", columnNames = {"blocker_id", "blocked_id"}),
        // 「相手にブロックされているか」（blocked_id で探す）の判定で使う
        indexes = @Index(name = "idx_blocks_blocked_blocker", columnList = "blocked_id, blocker_id"),
        check = @CheckConstraint(name = "ck_blocks_not_self", constraint = "blocker_id <> blocked_id"))
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ブロックした人。ユーザーを削除すると、ブロックの行も DB が自動で削除する（ON DELETE CASCADE） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User blocker;

    /** ブロックされた人。ユーザーを削除すると、ブロックの行も DB が自動で削除する（ON DELETE CASCADE） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User blocked;

    /** ブロックした日時（ブロック中の一覧の並び順に使う） */
    @Column(name = "blocked_at", nullable = false, updatable = false)
    private LocalDateTime blockedAt;

    protected Block() {
        // JPA 用
    }

    public Block(User blocker, User blocked) {
        if (blocker.getId().equals(blocked.getId())) {
            throw new IllegalArgumentException("自分自身はブロックできない");
        }
        this.blocker = blocker;
        this.blocked = blocked;
    }

    @PrePersist
    void onCreate() {
        this.blockedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getBlocker() {
        return blocker;
    }

    public User getBlocked() {
        return blocked;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }
}
