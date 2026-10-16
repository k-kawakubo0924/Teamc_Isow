package com.teamc.isow.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** パスワードの再設定の API の本文と応答（docs/auth.md「パスワードの再設定」） */
public final class PasswordResetRequests {

    private PasswordResetRequests() {
    }

    /** 再設定のメールを頼む（POST /api/auth/password-reset/request）。メールアドレスはログインと同じくそろえる */
    public record Request(
            @NotBlank(message = "メールアドレスを入力してください")
            @Email(message = "メールアドレスの形式が正しくありません")
            @Size(max = 255, message = "メールアドレスの形式が正しくありません")
            String email) {

        public Request {
            email = AuthInputNormalizer.email(email);
        }
    }

    /** 新しいパスワードを設定する（POST /api/auth/password-reset/confirm） */
    public record Confirm(
            @NotBlank(message = "リンクが正しくありません")
            String token,

            @NotBlank(message = "新しいパスワードを入力してください")
            @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
            String newPassword) {
    }

    /** 受け付けた（登録の有無にかかわらず同じ文言） */
    public record Accepted(String message) {
    }
}
