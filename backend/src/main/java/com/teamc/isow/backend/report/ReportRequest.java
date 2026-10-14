package com.teamc.isow.backend.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 通報のリクエスト（POST /api/reports。docs/admin.md「通報」）。種類・理由は定数名の文字列で受け取り、ReportService で確かめる
 *
 * @param targetType 対象の種類（ReportTargetType の定数名）
 * @param targetId 対象の ID（投稿・ユーザー・メッセージ）
 * @param reason 理由（ReportReason の定数名。対象の種類に合ったものだけ）
 * @param comment 詳しい内容（任意。理由が「その他」なら必須。500文字まで）
 */
public record ReportRequest(
        @NotBlank(message = "通報する対象を指定してください")
        String targetType,

        @NotNull(message = "通報する対象を指定してください")
        Long targetId,

        @NotBlank(message = "理由を選択してください")
        String reason,

        String comment) {

    public ReportRequest {
        comment = comment == null || comment.isBlank() ? null : comment.strip().replace("\r\n", "\n").replace('\r', '\n');
    }
}
