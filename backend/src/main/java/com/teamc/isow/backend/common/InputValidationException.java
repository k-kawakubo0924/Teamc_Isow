package com.teamc.isow.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DB の内容と照らし合わせて分かる入力エラー（存在しない選択肢など）。
 * アノテーションによる入力チェックと同じく 400 を返す。errors は項目名→画面に出すメッセージ
 */
public class InputValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public InputValidationException(Map<String, String> errors) {
        super("invalid input: " + errors.keySet());
        this.errors = new LinkedHashMap<>(errors);
    }

    public static InputValidationException of(String field, String message) {
        return new InputValidationException(Map.of(field, message));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
