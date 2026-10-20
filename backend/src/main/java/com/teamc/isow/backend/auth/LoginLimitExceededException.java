package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.common.RetryLaterException;
import java.time.Duration;

/**
 * ログインの失敗が続き、一時的に受け付けを止めている（429。docs/auth.md「ログインの失敗の回数の上限」）。
 * ①（メールアドレス × IP アドレス）と②（IP アドレス）のどちらで止まっても同じ文言にする。
 * 「このアカウントは」とは書かない（登録のないアドレスでも同じように止まり、同じ文言になる）
 */
public class LoginLimitExceededException extends RetryLaterException {

    public LoginLimitExceededException(Duration retryAfter) {
        super("login failure limit exceeded", retryAfter);
    }

    @Override
    public String userMessage() {
        return "ログインの失敗が続いたため、一時的に受け付けを止めています。あと約" + retryAfterMinutes()
                + "分で、もう一度お試しください。パスワードを忘れた場合は「パスワードをお忘れですか？」から再設定できます。";
    }
}
