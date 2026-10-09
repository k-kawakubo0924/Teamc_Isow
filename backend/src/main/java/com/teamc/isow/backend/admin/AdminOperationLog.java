package com.teamc.isow.backend.admin;

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
import org.hibernate.annotations.Immutable;

/**
 * 管理操作のログ（docs/admin.md「管理操作のログ」）。誰がいつ何をしたかの責任の記録。
 *
 * <ul>
 *   <li>追記だけ。保存後は変更しない（@Immutable と全列 updatable = false。値を変えるメソッドも作らない）。
 *       削除・更新のメソッドを持たないリポジトリ（AdminOperationLogRepository）から保存する</li>
 *   <li>操作と同じトランザクションで記録する（AdminOperationLogger）。残せなければ操作も取り消す</li>
 *   <li>接続元の IP アドレスは残さない。detail に個人を特定する情報（メールアドレスなど）を書かない</li>
 *   <li>操作した人には外部キーを張らず、操作した時点のユーザー名を列に持つ
 *       （将来ユーザーを物理削除しても、削除が失敗したりログが消えたりしないように。ユーザー名が変わっても過去の表示が変わらないように）</li>
 * </ul>
 */
@Entity
@Immutable
@Table(
        name = "admin_operation_logs",
        // 一覧を新しい順に並べるときに使う
        indexes = @Index(name = "idx_admin_operation_logs_operated_at", columnList = "operated_at"))
public class AdminOperationLog {

    /** システムの操作（起動時の昇格など、人が操作していないもの）のときに operator_username に入れる名前 */
    public static final String SYSTEM_OPERATOR_NAME = "システム";

    /** ログを一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 操作した管理者のユーザー ID。システムの操作のときは null。
     * 外部キーは張らない（将来ユーザーを物理削除しても、ログが消えたり削除が失敗したりしないように）
     */
    @Column(name = "operator_id", updatable = false)
    private Long operatorId;

    /**
     * 操作した人のユーザー名（操作した時点の値）。システムの操作のときは SYSTEM_OPERATOR_NAME（「システム」）。
     * ユーザー名は半角英数字と _ だけのため、実在するユーザー名とは重ならない（docs/auth.md）
     */
    @Column(name = "operator_username", nullable = false, updatable = false, length = 50)
    private String operatorUsername;

    /** 操作した日時 */
    @Column(name = "operated_at", nullable = false, updatable = false)
    private LocalDateTime operatedAt;

    /** 操作の種類。AdminAction の定数名を文字列で持つ（@Enumerated を使わない理由は AdminAction のコメント） */
    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private String action;

    /** 対象の種類。AdminTargetType の定数名を文字列で持つ */
    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private String targetType;

    /** 対象の ID（対象の表がいろいろなため、外部キーは張らない） */
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    /**
     * 内容（操作の中身が後から分かる短い説明。例：「名前：フェミニン」）。
     * 個人を特定する情報（メールアドレスなど）は書かない（docs/admin.md）
     */
    @Column(name = "detail", updatable = false, length = 1000)
    private String detail;

    protected AdminOperationLog() {
        // JPA 用
    }

    private AdminOperationLog(Long operatorId, String operatorUsername, AdminAction action,
            AdminTargetType targetType, Long targetId, String detail) {
        this.operatorId = operatorId;
        this.operatorUsername = operatorUsername;
        this.action = action.name();
        this.targetType = targetType.name();
        this.targetId = targetId;
        this.detail = detail;
    }

    /** 管理者の操作。操作した時点のユーザー名を残す */
    static AdminOperationLog byAdmin(User admin, AdminAction action, AdminTargetType targetType, Long targetId,
            String detail) {
        return new AdminOperationLog(admin.getId(), admin.getUsername(), action, targetType, targetId, detail);
    }

    /** システムの操作（起動時の昇格など）。操作した人の ID は持たず、名前は「システム」 */
    static AdminOperationLog bySystem(AdminAction action, AdminTargetType targetType, Long targetId, String detail) {
        return new AdminOperationLog(null, SYSTEM_OPERATOR_NAME, action, targetType, targetId, detail);
    }

    @PrePersist
    void onCreate() {
        this.operatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    /** システムの操作か */
    public boolean isSystem() {
        return operatorId == null;
    }

    public LocalDateTime getOperatedAt() {
        return operatedAt;
    }

    /** 文字列のまま返す（後の段階で種類を減らしても、過去のログを読めるようにするため） */
    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }
}
