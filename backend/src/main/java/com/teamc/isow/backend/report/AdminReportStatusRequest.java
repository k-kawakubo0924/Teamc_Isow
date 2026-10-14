package com.teamc.isow.backend.report;

/**
 * 通報の状態の変更（POST /api/admin/reports/{id}/status）
 *
 * @param status 新しい状態（ReportStatus の定数名）
 * @param memo 管理者のメモ（任意。1000文字まで。管理者にだけ見せ、ログには書かない）
 */
public record AdminReportStatusRequest(String status, String memo) {

    public AdminReportStatusRequest {
        memo = memo == null || memo.isBlank() ? null : memo.strip().replace("\r\n", "\n").replace('\r', '\n');
    }
}
