package com.teamc.isow.backend.dm;

import java.util.Map;

/**
 * DM・相談のエラー応答。ApiErrorResponse と同じ項目に、理由（reason）を加えたもの。
 * 画面は reason で表示を出し分ける。
 *
 * @param message 画面上部に出す警告文
 * @param errors 項目名→その欄の下に出す警告文（この応答では常に空）
 * @param reason 理由（ConsultationUnavailableReason・ConversationOperationError の定数名）
 */
public record DmErrorResponse(String message, Map<String, String> errors, String reason) {
}
