package com.teamc.isow.backend.common;

import java.util.Map;

/**
 * APIのエラー応答。
 * message は画面上部に出す警告文、errors は項目名→その欄の下に出す警告文。
 */
public record ApiErrorResponse(String message, Map<String, String> errors) {
}
