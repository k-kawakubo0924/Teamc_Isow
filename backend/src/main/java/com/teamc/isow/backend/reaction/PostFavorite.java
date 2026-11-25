package com.teamc.isow.backend.reaction;

import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.user.User;
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
 * お気に入り（docs/home.md）。自分が後で見返すための保存で、他人には見えない（件数も公開しない）。
 * 自分のプロフィールの「お気に入り」タブに表示する（docs/profile.md）。
 */
@Entity
@Table(
        name = "favorites",
        // 同じユーザーが同じ投稿を2回お気に入りにできないようにする（自分のお気に入り一覧を探すインデックスも兼ねる）
        uniqueConstraints = @UniqueConstraint(name = "uk_favorites_user_post", columnNames = {"user_id", "post_id"}),
        // 投稿を削除したときの連鎖削除で使う
        indexes = @Index(name = "idx_favorites_post_id", columnList = "post_id"))
public class PostFavorite {

    /** お気に入りを一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** お気に入りにしたユーザー（本人以外には見せない） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** お気に入りにした投稿。投稿を削除すると、この行も DB が自動で削除する（ON DELETE CASCADE） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    /** お気に入りにした日時（お気に入り一覧を追加した順に並べるために使う） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PostFavorite() {
        // JPA 用
    }

    public PostFavorite(User user, Post post) {
        this.user = user;
        this.post = post;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Post getPost() {
        return post;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
