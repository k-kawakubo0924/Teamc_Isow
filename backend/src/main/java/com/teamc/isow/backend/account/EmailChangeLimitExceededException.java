package com.teamc.isow.backend.account;

import com.teamc.isow.backend.common.RetryLaterException;
import java.time.Duration;

/**
 * メールアドレスの変更の申し込みが1時間に3回を超えた（429。docs/settings.md「メールアドレスの変更」）。
 * もう一度申し込めるまでの残り時間を持ち、文言（「あと約N分」）と Retry-After ヘッダーに使う
 */
public class EmailChangeLimitExceededException extends RetryLaterException {

    /** @param retryAfter もう一度申し込めるまでの残り時間（1秒より短ければ1秒として扱う） */
    public EmailChangeLimitExceededException(Duration retryAfter) {
        super("email change limit exceeded", retryAfter);
    }

    @Override
    public String userMessage() {
        return "メールアドレスの変更の手続きは1時間に3回までです。あと約" + retryAfterMinutes() + "分で、もう一度申し込めます。";
    }
}
