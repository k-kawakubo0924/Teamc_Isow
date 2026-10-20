package com.teamc.isow.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** 失敗の回数の制限（メモリの上だけ。docs/auth.md「ログインの失敗の回数の上限」） */
class FailureLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T01:00:00Z"));

    private FailureLimiter limiter(int maxKeys) {
        return new FailureLimiter(5, WINDOW, maxKeys, clock);
    }

    private static void acquireTimes(FailureLimiter limiter, String key, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(limiter.tryAcquire(key)).as("%s の %d 回目", key, i + 1).isEmpty();
        }
    }

    @Test
    void 上限の回数までは通し_超えると待ち時間を返す() {
        FailureLimiter limiter = limiter(100);
        acquireTimes(limiter, "a", 5);

        assertThat(limiter.tryAcquire("a")).contains(WINDOW);
    }

    @Test
    void 待ち時間は_最も古い失敗が15分を過ぎるまで() {
        FailureLimiter limiter = limiter(100);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("a");
            clock.advance(Duration.ofMinutes(1));
        }
        // 最初の失敗から5分たった。あと10分で最初の失敗が消える
        assertThat(limiter.tryAcquire("a")).contains(Duration.ofMinutes(10));
    }

    @Test
    void 止まっている間の試行は数えず_最も古い失敗が消えれば1回だけ通る() {
        FailureLimiter limiter = limiter(100);
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(1));
        acquireTimes(limiter, "a", 4);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("a")).isPresent();
        }

        clock.advance(Duration.ofMinutes(14));
        assertThat(limiter.tryAcquire("a")).isEmpty();
        assertThat(limiter.tryAcquire("a")).isPresent();
    }

    @Test
    void 失敗ではなかった1回分は_releaseで戻せる() {
        FailureLimiter limiter = limiter(100);
        acquireTimes(limiter, "a", 5);
        limiter.release("a");

        assertThat(limiter.tryAcquire("a")).isEmpty();
        assertThat(limiter.tryAcquire("a")).isPresent();
    }

    @Test
    void resetで回数が消える() {
        FailureLimiter limiter = limiter(100);
        acquireTimes(limiter, "a", 5);
        limiter.reset("a");

        acquireTimes(limiter, "a", 5);
        assertThat(limiter.keyCount()).isEqualTo(1);
    }

    @Test
    void 前方が一致するキーだけをまとめて消す() {
        FailureLimiter limiter = limiter(100);
        acquireTimes(limiter, "a\0ip1", 5);
        acquireTimes(limiter, "a\0ip2", 5);
        acquireTimes(limiter, "ab\0ip1", 5);
        acquireTimes(limiter, "b\0ip1", 5);

        limiter.resetKeysStartingWith("a\0");

        acquireTimes(limiter, "a\0ip1", 5);
        acquireTimes(limiter, "a\0ip2", 5);
        assertThat(limiter.tryAcquire("ab\0ip1")).isPresent();
        assertThat(limiter.tryAcquire("b\0ip1")).isPresent();
    }

    @Test
    void 前方が一致するキーがなければ何もしない() {
        FailureLimiter limiter = limiter(100);
        acquireTimes(limiter, "b\0ip1", 5);

        limiter.resetKeysStartingWith("a\0");

        assertThat(limiter.tryAcquire("b\0ip1")).isPresent();
        assertThat(limiter.keyCount()).isEqualTo(1);
    }

    @Test
    void キーごとに別に数える() {
        FailureLimiter limiter = limiter(100);
        acquireTimes(limiter, "a", 5);

        assertThat(limiter.tryAcquire("a")).isPresent();
        assertThat(limiter.tryAcquire("b")).isEmpty();
    }

    @Test
    void 記録がなくなったキーは消える() {
        FailureLimiter limiter = limiter(100);
        limiter.tryAcquire("a");
        limiter.release("a");
        limiter.tryAcquire("b");
        limiter.reset("b");

        assertThat(limiter.keyCount()).isZero();
    }

    // ---- キーの数の上限（攻撃者は好きなだけアドレスを作れるため、メモリが増え続けないように） ----

    @Test
    void キーの数は上限を超えない() {
        FailureLimiter limiter = limiter(3);
        for (int i = 0; i < 1000; i++) {
            limiter.tryAcquire("key" + i);
        }

        assertThat(limiter.keyCount()).isEqualTo(3);
    }

    @Test
    void 上限に達していても_新しいキーの失敗は数えられ_上限の回数で止まる() {
        FailureLimiter limiter = limiter(3);
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("filler" + i);
        }

        acquireTimes(limiter, "new", 5);
        assertThat(limiter.tryAcquire("new")).isPresent();
    }

    @Test
    void 上限に達しても_止まっているキーは捨てず_止まったまま() {
        FailureLimiter limiter = limiter(3);
        acquireTimes(limiter, "attacker", 5);
        // 止まっているキーが最も古くなってから、ほかのキーを大量に作って追い出そうとする
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 1000; i++) {
            limiter.tryAcquire("flood" + i);
        }

        assertThat(limiter.tryAcquire("attacker")).isPresent();
    }

    @Test
    void 上限に達したときは_期限の過ぎた記録しかないキーから先に捨てる() {
        FailureLimiter limiter = limiter(3);
        limiter.tryAcquire("old");
        clock.advance(WINDOW);
        acquireTimes(limiter, "counting", 4);
        limiter.tryAcquire("other");

        limiter.tryAcquire("new");

        // counting の4回は残っている（5回目で止まる）
        assertThat(limiter.tryAcquire("counting")).isEmpty();
        assertThat(limiter.tryAcquire("counting")).isPresent();
    }

    @Test
    void 上限に達したときは_止まっていないキーのうち_最後の失敗が最も古いものを捨てる() {
        FailureLimiter limiter = limiter(3);
        limiter.tryAcquire("older");
        clock.advance(Duration.ofMinutes(1));
        acquireTimes(limiter, "newer", 4);
        clock.advance(Duration.ofMinutes(1));
        limiter.tryAcquire("other");

        limiter.tryAcquire("new");

        assertThat(limiter.tryAcquire("newer")).isEmpty();
        assertThat(limiter.tryAcquire("newer")).isPresent();
    }

    @Test
    void すべてのキーが止まっていても_新しい失敗は数え_止まり終わりが最も近いキーを捨てる() {
        FailureLimiter limiter = limiter(2);
        // 止まり終わりが近い順と、最後の失敗が古い順が逆になるようにする（古い順に捨てる作りでは endsLater が捨てられる）
        // endsSooner：最初の失敗 0分・最後 10分 → 15分に止まり終わる。endsLater：5分に5回 → 20分に止まり終わる
        limiter.tryAcquire("endsSooner");
        clock.advance(Duration.ofMinutes(5));
        acquireTimes(limiter, "endsLater", 5);
        clock.advance(Duration.ofMinutes(5));
        acquireTimes(limiter, "endsSooner", 4);

        acquireTimes(limiter, "new", 5);

        assertThat(limiter.tryAcquire("new")).isPresent();
        assertThat(limiter.tryAcquire("endsLater")).isPresent();
        assertThat(limiter.keyCount()).isEqualTo(2);
    }

    // ---- 同時に送られたとき ----

    @Test
    void 同時に送られても_上限の回数より多くは通さない() throws Exception {
        FailureLimiter limiter = limiter(100);
        int threads = 32;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Optional<Duration>>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return limiter.tryAcquire("a");
                }));
            }
            start.countDown();

            int passed = 0;
            for (Future<Optional<Duration>> result : results) {
                if (result.get(10, TimeUnit.SECONDS).isEmpty()) {
                    passed++;
                }
            }
            assertThat(passed).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }
}
