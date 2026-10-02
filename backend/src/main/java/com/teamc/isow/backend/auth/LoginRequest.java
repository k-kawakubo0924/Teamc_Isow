package com.teamc.isow.backend.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * ログインのリクエスト。
 * メールアドレスは登録時と同じ正規化をしてから照合する。
 * 形式チェックは行わない（未入力以外はすべて認証失敗として同じ応答を返す）。
 */
public record LoginRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        String email,

        @NotBlank(message = "パスワードを入力してください")
        String password) {

    public LoginRequest {
        email = AuthInputNormalizer.email(email);
    }
}
