package com.teamc.isow.backend.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/** メールアドレス・ユーザ名がすでに登録されている場合に投げる。errors は項目名→画面に出すメッセージ */
public class DuplicateRegistrationException extends RuntimeException {

    private final Map<String, String> errors;

    public DuplicateRegistrationException(Map<String, String> errors) {
        super("duplicate registration: " + errors.keySet());
        this.errors = new LinkedHashMap<>(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
