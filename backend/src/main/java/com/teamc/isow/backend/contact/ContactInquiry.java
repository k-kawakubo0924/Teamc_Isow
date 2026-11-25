package com.teamc.isow.backend.contact;

import com.teamc.isow.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * お問い合わせ1件（docs/settings.md「お問い合わせ」、docs/admin.md「7. お問い合わせ」）。
 *
 * <ul>
 *   <li>内容とメールアドレスは個人情報を含みうる。アプリのログ・管理操作のログ（detail）には書かない</li>
 *   <li>保存期間は未確定で、<b>今は自動では消えない</b></li>
 *   <li>送った人・対応した管理者には外部キーを張らない（通報・操作ログと同じ。退会しても記録を残すため）</li>
 * </ul>
 */
@Entity
@Table(
        name = "contact_inquiries",
        indexes = {
                // 管理画面の一覧（状態で絞り、新しい順）
                @Index(name = "idx_contact_inquiries_status_created", columnList = "status, created_at"),
                // 1人1日の上限を数える
                @Index(name = "idx_contact_inquiries_user_created", columnList = "user_id, created_at")
        })
public class ContactInquiry {

    /** 内容の長さの上限（design/Contact us.png の「0 / 1000」） */
    public static final int MAX_BODY_LENGTH = 1000;

    /** 管理者のメモの長さの上限（通報と同じ） */
    public static final int MAX_MEMO_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 送った人の ID（外部キーは張らない） */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 受付の経路（ContactChannel の定数名） */
    @Column(name = "channel", nullable = false, length = 30, updatable = false)
    private String channel;

    /** 種類（ContactCategory の定数名） */
    @Column(name = "category", nullable = false, length = 20, updatable = false)
    private String category;

    /**
     * 返信先メールアドレス（任意）。登録済みのアドレスと違うものを入力したときだけ保存する
     * （登録済みのアドレスはすでに持っているため、もう一度預からない）
     */
    @Column(name = "email", length = 255, updatable = false)
    private String email;

    /** 内容 */
    @Column(name = "body", nullable = false, length = MAX_BODY_LENGTH, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 状態（ContactStatus の定数名） */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 対応を終えた（対応済み・対応不要にした）日時。未対応なら null */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 最後に状態を変えた管理者の ID と、その時点のユーザー名（外部キーは張らない） */
    @Column(name = "handled_by_id")
    private Long handledById;

    @Column(name = "handled_by_username", length = 50)
    private String handledByUsername;

    /** 管理者のメモ（管理者にだけ見せる。ログには書かない） */
    @Column(name = "admin_memo", length = MAX_MEMO_LENGTH)
    private String adminMemo;

    protected ContactInquiry() {
        // JPA 用
    }

    ContactInquiry(Long userId, ContactChannel channel, ContactCategory category, String email, String body,
            LocalDateTime now) {
        this.userId = userId;
        this.channel = channel.name();
        this.category = category.name();
        this.email = email;
        this.body = body;
        this.createdAt = now;
        this.status = ContactStatus.OPEN.name();
    }

    /** 状態を変える。対応を終えたら終了日時を、未対応に戻したら終了日時を消す */
    void changeStatus(ContactStatus newStatus, User admin, String memo, LocalDateTime now) {
        this.status = newStatus.name();
        this.closedAt = newStatus.isClosed() ? now : null;
        this.handledById = admin.getId();
        this.handledByUsername = admin.getUsername();
        this.adminMemo = memo;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public ContactChannel getChannel() {
        return ContactChannel.valueOf(channel);
    }

    public ContactCategory getCategory() {
        return ContactCategory.valueOf(category);
    }

    public String getEmail() {
        return email;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ContactStatus getStatus() {
        return ContactStatus.valueOf(status);
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public String getHandledByUsername() {
        return handledByUsername;
    }

    public String getAdminMemo() {
        return adminMemo;
    }
}
