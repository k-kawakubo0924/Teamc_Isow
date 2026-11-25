package com.teamc.isow.backend.common;

import java.time.Duration;

/**
 * 回数の上限に達し、時間をおけばもう一度できる（429）。もう一度できるまでの残り時間を持つ。
 *
 * <p>応答は {@link GlobalExceptionHandler} がまとめて作る：429、Retry-After ヘッダー（秒）、本文は {@link #userMessage()}。
 * 「時間をおいて」ではなく、文言で「あと約N分」と伝える（{@link #retryAfterMinutes()} を使う）。
 * 文言と例外の message に、メールアドレス・IP アドレスなどを入れないこと
 */
public abstract class RetryLaterException extends RuntimeException {

    private final Duration retryAfter;

    /**
     * @param message 例外の message（ログ用。画面には出さない）
     * @param retryAfter もう一度できるまでの残り時間（1秒より短ければ1秒として扱う）
     */
    protected RetryLaterException(String message, Duration retryAfter) {
        super(message);
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

    /** 画面にそのまま出す文言（「あと約N分」を含める） */
    public abstract String userMessage();
}
