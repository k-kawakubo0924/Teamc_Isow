package com.teamc.isow.backend.account;

import com.teamc.isow.backend.auth.AuthInputNormalizer;
import com.teamc.isow.backend.auth.PasswordRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** メールアドレスの変更・ログイン中のパスワードの変更の API の本文と応答（docs/settings.md） */
public final class AccountRequests {

    private AccountRequests() {
    }

    /**
     * メールアドレスの変更を申し込む（POST /api/users/me/email-change）。
     * 現在のパスワードを求める（設計画像にはない欄。docs/settings.md「設計画像と変えた点」）
     */
    public record EmailChange(
            @NotBlank(message = "現在のパスワードを入力してください")
            String currentPassword,

            @NotBlank(message = "新しいメールアドレスを入力してください")
            @Email(message = "メールアドレスの形式が正しくありません")
            @Size(max = 255, message = "メールアドレスの形式が正しくありません")
            String newEmail,

            @NotBlank(message = "確認用メールアドレスを入力してください")
            String confirmEmail) {

        public EmailChange {
            newEmail = AuthInputNormalizer.email(newEmail);
            confirmEmail = AuthInputNormalizer.email(confirmEmail);
        }
    }

    /** 確認のリンクの鍵で変更を完了する（POST /api/auth/email-change/confirm） */
    public record EmailChangeConfirm(@NotBlank(message = "リンクが正しくありません") String token) {
    }

    /** ログイン中にパスワードを変える（POST /api/users/me/password） */
    public record PasswordChange(
            @NotBlank(message = "現在のパスワードを入力してください")
            String currentPassword,

            @NotBlank(message = "新しいパスワードを入力してください")
            @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
            String newPassword) {
    }

    /** 受け付けた */
    public record Accepted(String message) {
    }
}
