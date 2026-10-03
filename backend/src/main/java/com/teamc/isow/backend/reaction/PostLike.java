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
 * いいね（docs/home.md）。投稿者への反応で、件数が公開される。
 * エンティティ名を Like にすると JPQL の LIKE と重なるため PostLike とする（テーブル名は likes）。
 */
@Entity
@Table(
        name = "likes",
        // 同じユーザーが同じ投稿に2回いいねできないようにする（自分がいいねした投稿を探すインデックスも兼ねる）
        uniqueConstraints = @UniqueConstraint(name = "uk_likes_user_post", columnNames = {"user_id", "post_id"}),
        // 投稿ごとのいいね件数の集計と、投稿を削除したときの連鎖削除で使う
        indexes = @Index(name = "idx_likes_post_id", columnList = "post_id"))
public class PostLike {

    /** いいねを一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** いいねしたユーザー */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** いいねされた投稿。投稿を削除すると、この行も DB が自動で削除する（ON DELETE CASCADE） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    /** いいねした日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PostLike() {
        // JPA 用
    }

    public PostLike(User user, Post post) {
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
