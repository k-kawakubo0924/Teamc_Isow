package com.teamc.isow.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** メモリの上だけで回数を数える制限（パスワードの再設定の IP アドレスごとの制限。docs/auth.md「送りすぎを防ぐ」） */
class InMemoryRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T01:00:00Z"));

    private InMemoryRateLimiter limiter(int maxKeys) {
        return new InMemoryRateLimiter(3, WINDOW, maxKeys, clock);
    }

    private static void acquireTimes(InMemoryRateLimiter limiter, String key, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(limiter.tryAcquire(key)).as("%s の %d 回目", key, i + 1).isTrue();
        }
    }

    @Test
    void 上限の回数までは通し_超えると断る() {
        InMemoryRateLimiter limiter = limiter(100);
        acquireTimes(limiter, "a", 3);

        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void 時間がたてば_また通る() {
        InMemoryRateLimiter limiter = limiter(100);
        acquireTimes(limiter, "a", 3);
        clock.advance(WINDOW);

        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    // ---- キーの数の上限（IPv6 なら攻撃者が使えるアドレスは大量にあり、プロキシの後ろではヘッダー由来の値がキーになるため） ----

    @Test
    void キーの数は上限を超えない() {
        InMemoryRateLimiter limiter = limiter(3);
        for (int i = 0; i < 1000; i++) {
            limiter.tryAcquire("key" + i);
        }

        assertThat(limiter.keyCount()).isEqualTo(3);
    }

    @Test
    void 上限に達していても_新しいキーは数えられ_上限の回数で断る() {
        InMemoryRateLimiter limiter = limiter(3);
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("filler" + i);
        }

        acquireTimes(limiter, "new", 3);
        assertThat(limiter.tryAcquire("new")).isFalse();
    }

    @Test
    void 上限に達しても_止まっているキーは捨てず_止まったまま() {
        InMemoryRateLimiter limiter = limiter(3);
        acquireTimes(limiter, "attacker", 3);
        // 止まっているキーが最も古くなってから、ほかのキーを大量に作って追い出そうとする
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 1000; i++) {
            limiter.tryAcquire("flood" + i);
        }

        assertThat(limiter.tryAcquire("attacker")).isFalse();
        assertThat(limiter.keyCount()).isEqualTo(3);
    }

    @Test
    void 上限に達したときは_止まっていないキーのうち_最後の記録が最も古いものを捨てる() {
        InMemoryRateLimiter limiter = limiter(3);
        limiter.tryAcquire("older");
        clock.advance(Duration.ofMinutes(1));
        acquireTimes(limiter, "newer", 2);
        clock.advance(Duration.ofMinutes(1));
        limiter.tryAcquire("other");

        limiter.tryAcquire("new");

        assertThat(limiter.tryAcquire("newer")).isTrue();
        assertThat(limiter.tryAcquire("newer")).isFalse();
        assertThat(limiter.keyCount()).isEqualTo(3);
    }

    @Test
    void すべてのキーが止まっていても_新しいキーは数え_止まり終わりが最も近いキーを捨てる() {
        InMemoryRateLimiter limiter = limiter(2);
        // 止まり終わりが近い順と、最後の記録が古い順が逆になるようにする
        // endsSooner：最初 0分・最後 6分 → 10分に止まり終わる。endsLater：3分に3回 → 13分に止まり終わる
        limiter.tryAcquire("endsSooner");
        clock.advance(Duration.ofMinutes(3));
        acquireTimes(limiter, "endsLater", 3);
        clock.advance(Duration.ofMinutes(3));
        acquireTimes(limiter, "endsSooner", 2);

        acquireTimes(limiter, "new", 3);

        assertThat(limiter.tryAcquire("new")).isFalse();
        assertThat(limiter.tryAcquire("endsLater")).isFalse();
        assertThat(limiter.keyCount()).isEqualTo(2);
    }
}
