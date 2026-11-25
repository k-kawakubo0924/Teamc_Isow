package com.teamc.isow.backend.follow;

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
 * フォロー関係（docs/profile.md）。
 *
 * <p>解除しても行は削除せず、解除日時を記録する（解除から5分以内は一覧に残し、押し間違いを戻せるようにするため）。
 * 解除から5分以内に再フォローした場合は、新しい行を作らずにこの行を有効に戻す（FollowService を参照）。
 */
@Entity
@Table(
        name = "follows",
        // 同じ組み合わせで有効なフォローは1つまで。active は有効なら TRUE、解除済みなら NULL で、
        // NULL どうしは重複とみなされないため、解除済みの行はいくつあってもよい。
        // 部分インデックス（WHERE unfollowed_at IS NULL）は JPA で定義できず、テストの H2 も対応していないためこの形にする。
        // フォロー中一覧（follower_id で探す）のインデックスも兼ねる
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follows_active", columnNames = {"follower_id", "followee_id", "active"}),
        // フォロワー一覧で使う
        indexes = @Index(name = "idx_follows_followee_id", columnList = "followee_id"),
        check = @CheckConstraint(name = "ck_follows_not_self", constraint = "follower_id <> followee_id"))
public class Follow {

    /** フォロー関係を一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** フォローする側 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    /** フォローされる側 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    /** フォローした日時。解除から5分以内に戻した場合は変えない（一覧の並び順と「〇日前」の表示に使う） */
    @Column(name = "followed_at", nullable = false, updatable = false)
    private LocalDateTime followedAt;

    /** 解除した日時。有効なフォローは null */
    @Column(name = "unfollowed_at")
    private LocalDateTime unfollowedAt;

    /** 有効なら TRUE、解除済みなら NULL（FALSE は使わない）。一意制約のための列で、unfollowed_at と必ず合わせて更新する */
    @Column(name = "active")
    private Boolean active;

    /**
     * 解除から5分以内の再フォローで、この行を有効に戻した日時（最後に戻した日時）。戻したことがなければ null。
     * フォローの通知は、新しい行を作ったときだけ送る（戻した場合は送らない）。通知機能を作るときの判定に使う
     */
    @Column(name = "restored_at")
    private LocalDateTime restoredAt;

    protected Follow() {
        // JPA 用
    }

    public Follow(User follower, User followee) {
        this.follower = follower;
        this.followee = followee;
        this.active = Boolean.TRUE;
    }

    @PrePersist
    void onCreate() {
        this.followedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getFollower() {
        return follower;
    }

    public User getFollowee() {
        return followee;
    }

    public LocalDateTime getFollowedAt() {
        return followedAt;
    }

    public LocalDateTime getUnfollowedAt() {
        return unfollowedAt;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public LocalDateTime getRestoredAt() {
        return restoredAt;
    }
}
