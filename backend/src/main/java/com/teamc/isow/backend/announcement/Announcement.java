package com.teamc.isow.backend.announcement;

import com.teamc.isow.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 運営からのお知らせ（docs/admin.md「お知らせの発行」）。管理者が管理画面から発行する。
 *
 * <ul>
 *   <li>題名・本文は文字だけ。HTML として扱わない（画面でも dangerouslySetInnerHTML は使わない）</li>
 *   <li>発行後の訂正・取り消しは未確定（docs/admin.md「未確定・要確認」）。今は値を変えるメソッドを作らない</li>
 *   <li>発行した管理者には外部キーを張らず、発行した時点のユーザー名を列に持つ
 *       （管理操作のログと同じ理由。将来ユーザーを物理削除しても、お知らせが消えたり削除が失敗したりしないように）</li>
 * </ul>
 */
@Entity
@Table(
        name = "announcements",
        // 一覧を新しい順に並べるときに使う
        indexes = @Index(name = "idx_announcements_published_at", columnList = "published_at"))
public class Announcement {

    /** 題名の文字数の上限（投稿の題名と同じ） */
    public static final int MAX_TITLE_LENGTH = 100;

    /** 本文の文字数の上限（投稿説明と同じ） */
    public static final int MAX_BODY_LENGTH = 2000;

    /** お知らせを一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 題名 */
    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    /** 本文（文字だけ。改行は \n にそろえて保存する） */
    @Column(nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    /** 発行した日時 */
    @Column(name = "published_at", nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    /** 発行した管理者のユーザー ID（外部キーは張らない） */
    @Column(name = "publisher_id", nullable = false, updatable = false)
    private Long publisherId;

    /** 発行した管理者のユーザー名（発行した時点の値） */
    @Column(name = "publisher_username", nullable = false, updatable = false, length = 50)
    private String publisherUsername;

    protected Announcement() {
        // JPA 用
    }

    public Announcement(User publisher, String title, String body) {
        this.publisherId = publisher.getId();
        this.publisherUsername = publisher.getUsername();
        this.title = title;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        this.publishedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public String getPublisherUsername() {
        return publisherUsername;
    }
}
