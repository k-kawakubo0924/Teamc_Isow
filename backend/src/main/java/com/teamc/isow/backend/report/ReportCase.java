package com.teamc.isow.backend.report;

import com.teamc.isow.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 通報の対象ごとのまとまり（docs/admin.md「通報」）。同じ対象への通報は、未対応の間は1つにまとめる。
 * 1人分の通報は Report、証拠の写真は ReportEvidenceFile。
 *
 * <ul>
 *   <li>証拠（通報した時点の内容のコピー）は、最初の通報のときに1回だけ取る（evidence_json と ReportEvidenceFile）</li>
 *   <li>対応を終えた（対応済み・却下）後に同じ対象が通報されたら、新しいまとまりを作り、証拠も取り直す</li>
 *   <li>対応を終えてから180日を過ぎたら、証拠を消す（EvidencePurger）。まとまりと通報の行（理由・状態・日時）は残す</li>
 *   <li>対象・通報された人・対応した管理者には外部キーを張らない（対象が消えても、証拠と記録を残すため）</li>
 * </ul>
 */
@Entity
@Table(
        name = "report_cases",
        // 同じ対象の未対応のまとまりは1つまで。open は未対応なら TRUE、対応を終えたら NULL（NULL どうしは重複とみなされない。
        // フォローの active と同じ作り）
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_cases_open_target", columnNames = {"target_type", "target_id", "open"}),
        indexes = {
                // 管理画面の一覧（状態で絞り、新しい順）
                @Index(name = "idx_report_cases_status_last", columnList = "status, last_reported_at"),
                // 保存期間を過ぎた証拠を探す
                @Index(name = "idx_report_cases_closed_at", columnList = "closed_at")
        })
public class ReportCase {

    /** 一覧の要約（summary）の長さの上限 */
    static final int MAX_SUMMARY_LENGTH = 200;

    /** 管理者のメモの長さの上限 */
    public static final int MAX_MEMO_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 対象の種類（ReportTargetType の定数名） */
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private String targetType;

    /** 対象の ID（投稿・ユーザー・メッセージ） */
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    /** 通報された人（投稿者・ユーザー本人・メッセージを送った人）の ID */
    @Column(name = "target_user_id", nullable = false, updatable = false)
    private Long targetUserId;

    /** 状態（ReportStatus の定数名） */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 未対応なら TRUE、対応を終えたら NULL（一意制約のための列。status と必ず合わせて変える） */
    @Column(name = "open")
    private Boolean open;

    /** 通報の数（通報した人の数） */
    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "first_reported_at", nullable = false, updatable = false)
    private LocalDateTime firstReportedAt;

    @Column(name = "last_reported_at", nullable = false)
    private LocalDateTime lastReportedAt;

    /**
     * 管理画面の一覧に出す短い説明（投稿の題名・「@ユーザー名」・「@ユーザー名 のメッセージ」）。
     * DM の本文は一覧に出さない（見たことを記録する詳細でだけ出す）。証拠を消すときに一緒に消す
     */
    @Column(name = "summary", length = MAX_SUMMARY_LENGTH)
    private String summary;

    /** 通報した時点の内容のコピー（EvidenceSnapshot の JSON）。証拠を消すと null */
    @Column(name = "evidence_json", length = 200_000)
    private String evidenceJson;

    /** 保存期間を過ぎて証拠を消した日時。消していなければ null */
    @Column(name = "evidence_purged_at")
    private LocalDateTime evidencePurgedAt;

    /** 対応を終えた（対応済み・却下にした）日時。未対応なら null。証拠の保存期間はここから数える */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 最後に状態を変えた管理者の ID と、その時点のユーザー名（外部キーは張らない。管理操作のログと同じ理由） */
    @Column(name = "handled_by_id")
    private Long handledById;

    @Column(name = "handled_by_username", length = 50)
    private String handledByUsername;

    /** 管理者のメモ（管理者にだけ見せる。ログには書かない） */
    @Column(name = "admin_memo", length = MAX_MEMO_LENGTH)
    private String adminMemo;

    protected ReportCase() {
        // JPA 用
    }

    ReportCase(ReportTargetType targetType, Long targetId, Long targetUserId, String summary, String evidenceJson,
            LocalDateTime now) {
        this.targetType = targetType.name();
        this.targetId = targetId;
        this.targetUserId = targetUserId;
        this.summary = truncate(summary);
        this.evidenceJson = evidenceJson;
        this.firstReportedAt = now;
        this.lastReportedAt = now;
        changeStatus(ReportStatus.OPEN);
    }

    /** 通報が1件増えた */
    void addReport(LocalDateTime now) {
        this.reportCount++;
        this.lastReportedAt = now;
    }

    /** 状態を変える。対応を終えたら終了日時を、未対応に戻したら終了日時を消す */
    void changeStatus(ReportStatus newStatus, User admin, String memo, LocalDateTime now) {
        changeStatus(newStatus);
        this.closedAt = newStatus.isClosed() ? now : null;
        this.handledById = admin.getId();
        this.handledByUsername = admin.getUsername();
        this.adminMemo = memo;
    }

    /** 保存期間を過ぎた証拠を消した。要約も内容の一部のため消す */
    void purgeEvidence(LocalDateTime now) {
        this.evidenceJson = null;
        this.summary = null;
        this.evidencePurgedAt = now;
    }

    private void changeStatus(ReportStatus newStatus) {
        this.status = newStatus.name();
        this.open = newStatus.isClosed() ? null : Boolean.TRUE;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_SUMMARY_LENGTH ? value : value.substring(0, MAX_SUMMARY_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public ReportTargetType getTargetType() {
        return ReportTargetType.valueOf(targetType);
    }

    public Long getTargetId() {
        return targetId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public ReportStatus getStatus() {
        return ReportStatus.valueOf(status);
    }

    public int getReportCount() {
        return reportCount;
    }

    public LocalDateTime getFirstReportedAt() {
        return firstReportedAt;
    }

    public LocalDateTime getLastReportedAt() {
        return lastReportedAt;
    }

    public String getSummary() {
        return summary;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public LocalDateTime getEvidencePurgedAt() {
        return evidencePurgedAt;
    }

    public boolean isEvidencePurged() {
        return evidencePurgedAt != null;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public Long getHandledById() {
        return handledById;
    }

    public String getHandledByUsername() {
        return handledByUsername;
    }

    public String getAdminMemo() {
        return adminMemo;
    }
}
