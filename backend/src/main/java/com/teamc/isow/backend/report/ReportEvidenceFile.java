package com.teamc.isow.backend.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 証拠として複製した写真1枚（docs/admin.md「通報」）。投稿の写真・プロフィール画像・DM の画像を、通報した時点で複製したもの。
 *
 * <ul>
 *   <li>ファイルは公開フォルダ（/uploads/**）の外の証拠の保存先に置く（EvidenceStorage）。URL は持たず、ファイル名だけを持つ</li>
 *   <li>配信は管理者向けの API（GET /api/admin/reports/{caseId}/evidence-files/{id}）だけが、管理者であることを確かめてから行う</li>
 *   <li>保存期間を過ぎて消すときは、ここに記録したファイル名だけを消す（パターン・フォルダの一覧から探して消すことはしない）</li>
 * </ul>
 */
@Entity
@Table(name = "report_evidence_files")
public class ReportEvidenceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private ReportCase reportCase;

    /** 表示順（投稿の写真の順。1から） */
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    /** 証拠の保存先でのファイル名（推測できない乱数の名前） */
    @Column(name = "file_name", nullable = false, length = 100, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 50, updatable = false)
    private String contentType;

    /** DM の画像のとき、そのメッセージの ID（どのメッセージの画像かを画面で示すため）。それ以外は null */
    @Column(name = "message_id", updatable = false)
    private Long messageId;

    protected ReportEvidenceFile() {
        // JPA 用
    }

    ReportEvidenceFile(ReportCase reportCase, int sortOrder, String fileName, String contentType, Long messageId) {
        this.reportCase = reportCase;
        this.sortOrder = sortOrder;
        this.fileName = fileName;
        this.contentType = contentType;
        this.messageId = messageId;
    }

    public Long getId() {
        return id;
    }

    public ReportCase getReportCase() {
        return reportCase;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getMessageId() {
        return messageId;
    }
}
