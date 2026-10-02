package com.teamc.isow.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新規会員登録のリクエスト。
 * 受け取った時点で値を正規化し、入力チェックは正規化後の値に対して行う。
 * パスワードは前後の空白も含めてそのまま扱う。
 */
public record RegisterRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません")
        @Size(max = 255, message = "メールアドレスの形式が正しくありません")
        String email,

        @NotBlank(message = "電話番号を入力してください")
        @Pattern(regexp = "^0\\d{9,10}$", message = "電話番号は10〜11桁の数字で入力してください")
        String phoneNumber,

        @NotBlank(message = "パスワードを入力してください")
        // BCrypt は73バイト目以降を無視するため上限を72文字とする
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "8文字以上、英字と数字を含めてください")
        String password,

        @NotBlank(message = "ユーザー名を入力してください")
        @Pattern(regexp = "^[A-Za-z0-9_]{1,30}$", message = "ユーザー名は半角英数字と_で30文字以内で入力してください")
        String username) {

    public RegisterRequest {
        email = AuthInputNormalizer.email(email);
        phoneNumber = AuthInputNormalizer.phoneNumber(phoneNumber);
        username = AuthInputNormalizer.username(username);
    }
}
