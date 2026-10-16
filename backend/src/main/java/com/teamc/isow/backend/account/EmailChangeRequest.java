package com.teamc.isow.backend.account;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * メールアドレスの変更の申し込み（docs/settings.md「メールアドレスの変更」）。
 *
 * <ul>
 *   <li>確認のリンクの鍵には、この行の ID だけを入れる（新しいアドレスを URL・鍵に入れないため）</li>
 *   <li>変更待ちは1人1件だけ。新しく申し込むと、前の申し込みは無効（superseded）になる</li>
 *   <li>完了・無効になったら、新しいアドレス（new_email）を消す。行は回数の制限（1時間に3回）を数えるために残す</li>
 * </ul>
 */
@Entity
@Table(
        name = "email_change_requests",
        // 回数の制限（1時間に3回）と、変更待ちを探す
        indexes = @Index(name = "idx_email_change_requests_user_created", columnList = "user_id, created_at"))
public class EmailChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** 新しいメールアドレス。変更待ちの間だけ持つ（完了・無効になったら null） */
    @Column(name = "new_email", length = 255)
    private String newEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 変更を完了した日時 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 新しい申し込み・アドレスの重なりで無効にした日時 */
    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    protected EmailChangeRequest() {
        // JPA 用
    }

    /** 変更待ちの申し込み */
    static EmailChangeRequest pending(User user, String newEmail, LocalDateTime now) {
        EmailChangeRequest request = new EmailChangeRequest();
        request.user = user;
        request.newEmail = newEmail;
        request.createdAt = now;
        return request;
    }

    /** 回数だけを数える申し込み（新しいアドレスがすでに使われていた。変更待ちにはしない） */
    static EmailChangeRequest rejected(User user, LocalDateTime now) {
        EmailChangeRequest request = new EmailChangeRequest();
        request.user = user;
        request.createdAt = now;
        request.supersededAt = now;
        return request;
    }

    boolean isPending() {
        return newEmail != null && completedAt == null && supersededAt == null;
    }

    void supersede(LocalDateTime now) {
        this.supersededAt = now;
        this.newEmail = null;
    }

    void complete(LocalDateTime now) {
        this.completedAt = now;
        this.newEmail = null;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getNewEmail() {
        return newEmail;
    }
}
