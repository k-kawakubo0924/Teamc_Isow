package com.teamc.isow.backend.dm;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 相談の申込のリクエスト（POST /api/conversations。JSON）。
 *
 * @param recipientId 申し込む相手のユーザーID。必須
 * @param message 一言メッセージ（任意）。前後の空白を除いて空なら、メッセージは保存しない
 */
public record ConsultationRequest(
        @NotNull(message = "相談する相手を指定してください")
        Long recipientId,

        @Size(max = MAX_MESSAGE_LENGTH, message = "メッセージは" + MAX_MESSAGE_LENGTH + "文字以内で入力してください")
        String message) {

    /** messages.body の列の長さと同じ */
    static final int MAX_MESSAGE_LENGTH = 1000;

    /** 前後の空白を除いた一言メッセージ。空なら null */
    String strippedMessage() {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.strip();
    }
}
