package com.teamc.isow.backend.report;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 1人分の通報（docs/admin.md「通報」）。対象ごとのまとまり（ReportCase）に属する。
 * 通報した人は管理者にだけ見せる（通報された人には、通報されたことも、誰が通報したかも伝えない）
 */
@Entity
@Table(
        name = "reports",
        // 同じまとまりへの通報は1人1件まで（同じ人が同じ対象を何度通報しても1件のまま）
        uniqueConstraints = @UniqueConstraint(name = "uk_reports_case_reporter", columnNames = {"case_id", "reporter_id"}),
        // 1人1日の上限を数える
        indexes = @Index(name = "idx_reports_reporter_reported_at", columnList = "reporter_id, reported_at"))
public class Report {

    /** 詳しい内容の長さの上限 */
    public static final int MAX_COMMENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private ReportCase reportCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;

    /** 理由（ReportReason の定数名） */
    @Column(name = "reason", nullable = false, length = 30, updatable = false)
    private String reason;

    /** 詳しい内容（任意。「その他」のときは必須）。ログには書かない */
    @Column(name = "comment", length = MAX_COMMENT_LENGTH, updatable = false)
    private String comment;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    protected Report() {
        // JPA 用
    }

    Report(ReportCase reportCase, User reporter, ReportReason reason, String comment, LocalDateTime now) {
        this.reportCase = reportCase;
        this.reporter = reporter;
        this.reason = reason.name();
        this.comment = comment;
        this.reportedAt = now;
    }

    public Long getId() {
        return id;
    }

    public ReportCase getReportCase() {
        return reportCase;
    }

    public User getReporter() {
        return reporter;
    }

    public ReportReason getReason() {
        return ReportReason.valueOf(reason);
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }
}
