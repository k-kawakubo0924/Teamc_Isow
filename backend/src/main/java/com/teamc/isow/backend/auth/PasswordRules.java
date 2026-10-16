package com.teamc.isow.backend.auth;

/**
 * パスワードの規則（docs/auth.md「新規会員登録」）。新規会員登録・パスワードの再設定・ログイン中の変更で同じものを使う
 * （画面側は frontend/src/validation/authRules.ts。変えるときは両方を直すこと）
 */
public final class PasswordRules {

    /** 8〜72文字で、英字と数字を含む（BCrypt は73バイト目以降を無視するため上限を72文字とする） */
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";

    public static final String MESSAGE = "8文字以上、英字と数字を含めてください";

    private PasswordRules() {
    }
}
