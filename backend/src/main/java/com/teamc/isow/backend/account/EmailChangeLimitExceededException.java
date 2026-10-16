package com.teamc.isow.backend.account;

import java.time.Duration;

/**
 * メールアドレスの変更の申し込みが1時間に3回を超えた（429。docs/settings.md「メールアドレスの変更」）。
 * もう一度申し込めるまでの残り時間を持ち、文言（「あと約N分」）と Retry-After ヘッダーに使う
 */
public class EmailChangeLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    /** @param retryAfter もう一度申し込めるまでの残り時間（1秒より短ければ1秒として扱う） */
    public EmailChangeLimitExceededException(Duration retryAfter) {
        super("email change limit exceeded");
        this.retryAfter = retryAfter.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : retryAfter;
    }

    /** Retry-After ヘッダーの秒（端数は切り上げ） */
    public long retryAfterSeconds() {
        return retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
    }

    /** 文言に出す分（端数は切り上げ。1分に満たなくても「あと約1分」） */
    public long retryAfterMinutes() {
        return Math.max(1, (retryAfterSeconds() + 59) / 60);
    }
}
