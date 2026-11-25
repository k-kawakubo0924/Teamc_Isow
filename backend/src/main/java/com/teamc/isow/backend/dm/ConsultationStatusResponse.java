package com.teamc.isow.backend.dm;

import java.time.LocalDateTime;

/**
 * 相手に相談を申し込めるか（GET /api/users/{id}/consultation-status）。プロフィール画面の「相談する」ボタンの状態に使う。
 *
 * @param available 申し込めるか
 * @param reason 申し込めない理由。申し込める場合は null
 * @param message 申し込めない理由の表示文言。申し込める場合は null
 * @param availableAt 再度申し込めるようになる日時。reason が REJECTED_RECENTLY の場合だけ入れる
 * @param conversationId 申請中・進行中の会話のID。reason が ALREADY_REQUESTED・REQUEST_RECEIVED・IN_PROGRESS の場合だけ入れる
 */
public record ConsultationStatusResponse(
        boolean available,
        ConsultationUnavailableReason reason,
        String message,
        LocalDateTime availableAt,
        Long conversationId) {

    static ConsultationStatusResponse ofAvailable() {
        return new ConsultationStatusResponse(true, null, null, null, null);
    }

    static ConsultationStatusResponse ofUnavailable(
            ConsultationUnavailableReason reason, LocalDateTime availableAt, Long conversationId) {
        return new ConsultationStatusResponse(false, reason, reason.getMessage(), availableAt, conversationId);
    }
}
