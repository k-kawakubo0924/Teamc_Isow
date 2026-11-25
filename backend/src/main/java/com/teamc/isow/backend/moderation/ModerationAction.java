package com.teamc.isow.backend.moderation;

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
 * 措置の履歴（docs/admin.md「6. 措置」）。投稿の非公開化・公開に戻す・利用停止・解除を、1回ごとに1行で残す。
 *
 * <ul>
 *   <li>いまの状態は posts.hidden_at・users.suspended_at に持ち、ここは「いつ・誰が・なぜ」の履歴だけ。管理画面で見せる</li>
 *   <li>管理操作のログ（admin_operation_logs）と違い、管理者のメモを持つ（管理者にだけ見せる。ログには書かない）</li>
 *   <li>対象・管理者・通報には外部キーを張らない（対象が消えても履歴を残すため。管理操作のログ・通報と同じ）</li>
 * </ul>
 */
@Entity
@Table(name = "moderation_actions",
        indexes = @Index(name = "idx_moderation_actions_target", columnList = "target_type, target_id, created_at"))
public class ModerationAction {

    /** 管理者のメモの長さの上限（通報のメモと同じ） */
    public static final int MAX_MEMO_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 措置の種類（ModerationActionType の定数名。@Enumerated を使わない理由は AdminAction と同じ） */
    @Column(name = "action", nullable = false, updatable = false, length = 30)
    private String action;

    /** 対象の種類（POST・USER） */
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    /** 措置を受けた人（投稿者・ユーザー本人）の ID */
    @Column(name = "target_user_id", nullable = false, updatable = false)
    private Long targetUserId;

    /** 理由（ReportReason の定数名）。公開に戻す・解除では null */
    @Column(name = "reason", updatable = false, length = 30)
    private String reason;

    /** 利用停止の期限。無期限・利用停止以外では null */
    @Column(name = "suspended_until", updatable = false)
    private LocalDateTime suspendedUntil;

    /** 結びつけた通報（report_cases の ID）。通報からでなければ null */
    @Column(name = "report_case_id", updatable = false)
    private Long reportCaseId;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;

    /** 措置した時点の管理者のユーザー名 */
    @Column(name = "admin_username", nullable = false, updatable = false, length = 50)
    private String adminUsername;

    @Column(name = "memo", updatable = false, length = MAX_MEMO_LENGTH)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ModerationAction() {
        // JPA 用
    }

    ModerationAction(ModerationActionType action, Long targetId, Long targetUserId, String reason,
            LocalDateTime suspendedUntil, Long reportCaseId, User admin, String memo, LocalDateTime now) {
        this.action = action.name();
        this.targetType = action.getTargetType();
        this.targetId = targetId;
        this.targetUserId = targetUserId;
        this.reason = reason;
        this.suspendedUntil = suspendedUntil;
        this.reportCaseId = reportCaseId;
        this.adminId = admin.getId();
        this.adminUsername = admin.getUsername();
        this.memo = memo;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public ModerationActionType getAction() {
        return ModerationActionType.valueOf(action);
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getSuspendedUntil() {
        return suspendedUntil;
    }

    public Long getReportCaseId() {
        return reportCaseId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getMemo() {
        return memo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
