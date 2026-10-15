package com.teamc.isow.backend.moderation;

import com.teamc.isow.backend.report.ReportReason;
import com.teamc.isow.backend.user.User;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 利用停止中のユーザーへの応答（403。docs/admin.md「6. 措置」）。ログイン・すべての API で同じ形にする。
 * 画面は reason が ACCOUNT_SUSPENDED のとき、利用停止の画面（理由・期限・申し立て）を出す
 *
 * @param suspendedUntil 期限。無期限なら null
 * @param suspensionReason 理由（ReportReason の定数名）
 * @param suspensionReasonLabel 理由の表示名
 * @param appealToken 申し立て用の鍵（SuspensionAppealTokens。docs/settings.md「お問い合わせ」）。
 *     この応答を受け取れるのは、パスワードが正しいログインか有効なトークンを持つ本人だけ
 */
public record SuspendedErrorResponse(String message, Map<String, String> errors, String reason,
        LocalDateTime suspendedUntil, String suspensionReason, String suspensionReasonLabel, String appealToken) {

    static final String REASON = "ACCOUNT_SUSPENDED";

    public static SuspendedErrorResponse of(User user, String appealToken) {
        String reason = user.getSuspensionReason();
        String label = null;
        if (reason != null) {
            try {
                label = ReportReason.valueOf(reason).getLabel();
            } catch (IllegalArgumentException e) {
                label = reason;
            }
        }
        return new SuspendedErrorResponse("このアカウントは利用停止中です。", Map.of(), REASON, user.getSuspendedUntil(),
                reason, label, appealToken);
    }
}
