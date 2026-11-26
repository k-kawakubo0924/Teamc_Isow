package com.teamc.isow.backend.mute;

import com.teamc.isow.backend.user.User;
import jakarta.persistence.CheckConstraint;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * ミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」）。「muter が muted をミュートした」を1行で持つ。
 *
 * <p>効果は片方向で、muter の画面に流れてくる一覧（ホーム・投稿の検索・ほかの人の「フォロー中の投稿」）から muted の投稿を除くだけ。
 * ブロックと違い、何も削除せず、muted からは何も変わらない。
 * <b>DM の「通知オフ」（conversation_user_settings.muted_at。docs/dm.md「通知オフ」）とは別のもの</b>。
 * 解除できるのは、ミュートした本人（muter）だけ。解除は行を削除する
 */
@Entity
@Table(
        name = "user_mutes",
        // 同じ組み合わせは1行まで。一覧（muter_id で探す）と、流れてくる一覧から除く判定のインデックスも兼ねる
        uniqueConstraints = @UniqueConstraint(name = "uk_user_mutes_muter_muted", columnNames = {"muter_id", "muted_id"}),
        check = @CheckConstraint(name = "ck_user_mutes_not_self", constraint = "muter_id <> muted_id"))
public class UserMute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ミュートした人。ユーザーを削除すると、ミュートの行も DB が自動で削除する（ON DELETE CASCADE） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muter_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User muter;

    /** ミュートされた人。ユーザーを削除すると、ミュートの行も DB が自動で削除する（ON DELETE CASCADE） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muted_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User muted;

    /** ミュートした日時（一覧の並び順に使う）。アプリの Clock から渡す */
    @Column(name = "muted_at", nullable = false, updatable = false)
    private LocalDateTime mutedAt;

    protected UserMute() {
        // JPA 用
    }

    public UserMute(User muter, User muted, LocalDateTime mutedAt) {
        if (muter.getId().equals(muted.getId())) {
            throw new IllegalArgumentException("自分自身はミュートできない");
        }
        this.muter = muter;
        this.muted = muted;
        this.mutedAt = mutedAt;
    }

    public Long getId() {
        return id;
    }

    public User getMuter() {
        return muter;
    }

    public User getMuted() {
        return muted;
    }

    public LocalDateTime getMutedAt() {
        return mutedAt;
    }
}
