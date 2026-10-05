package com.teamc.isow.backend.dm;

import java.util.Map;

/**
 * 相談を申し込めない場合のエラー応答。ApiErrorResponse と同じ項目に、理由（reason）を加えたもの。
 *
 * @param message 画面上部に出す警告文
 * @param errors 項目名→その欄の下に出す警告文（この応答では常に空）
 * @param reason 申し込めない理由（ConsultationUnavailableReason の定数名）
 */
public record ConsultationErrorResponse(String message, Map<String, String> errors, ConsultationUnavailableReason reason) {
}
