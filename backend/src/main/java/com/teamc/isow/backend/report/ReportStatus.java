package com.teamc.isow.backend.report;

/**
 * 通報（対象ごと）の状態（docs/admin.md「通報」）。DB には定数名を文字列で保存する。
 *
 * <p>措置（投稿の非公開化・削除、ユーザーの利用停止）はまだ作っていないため、「対応済み」にしても実際の措置は行われない。
 * 状態は、管理者が確かめた結果を記録するだけ
 */
public enum ReportStatus {

    /** 未対応 */
    OPEN("未対応"),
    /** 対応済み（確かめて対応した。措置は手作業か、措置の機能ができてから） */
    RESOLVED("対応済み"),
    /** 却下（問題がないと判断した） */
    DISMISSED("却下");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 対応を終えた状態か（証拠の保存期間を数え始める） */
    public boolean isClosed() {
        return this != OPEN;
    }
}
