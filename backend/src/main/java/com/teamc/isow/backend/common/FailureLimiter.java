package com.teamc.isow.backend.common;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * メモリの上だけで失敗の回数を数える制限（キーごとに、window の間に maxFailures 回まで。docs/auth.md「ログインの失敗の回数の上限」）。
 *
 * <p><b>DB にもアプリのログにも保存しない</b>。サーバーを再起動すれば消え、window を過ぎた記録はその都度捨てる
 * （IP アドレスをキーにしてよいのは、この使い方のときだけ。{@link InMemoryRateLimiter} と同じ）。
 * キー（メールアドレス・IP アドレスなど）をログ・例外の message に書かないこと。
 *
 * <p>使い方：照合の<b>前</b>に {@link #tryAcquire} を呼ぶ（その時点で1回分を失敗として数える）。照合が成功したなど、
 * 失敗ではなかったときは {@link #release} か {@link #reset} で戻す。先に数えるのは、照合の間に同時に送られた分まで
 * 通してしまわないため（確かめてから照合の後に数える作りでは、同時に送れば上限を超えて照合させられる）。
 *
 * <p><b>キーの数には上限（maxKeys）がある</b>。ログインでは攻撃者が好きなだけアドレスを作れるため、定期的な掃除だけでは
 * 作られる速さに追いつかない。上限に達したら、新しいキーの失敗を数えられるよう、次の順で1つ捨てる。
 * 新しい失敗を数えずに通す（＝上限に達するとすり抜けられる）ことはしない。
 * <ol>
 *   <li>window を過ぎた記録しかないキー（捨てても何も変わらない）</li>
 *   <li>止まっていないキーのうち、最後の失敗が最も古いもの</li>
 *   <li>すべて止まっているときだけ、止まり終わりが最も近いもの</li>
 * </ol>
 * 止まっているキーを追い出すには、上限の数だけほかのキーを止めなければならない。ログインでは IP ごとの上限があるため、
 * そのための IP の数で直接パスワードを試すほうが多く試せる（追い出しで得をしない）
 */
public class FailureLimiter {

    private final int maxFailures;
    private final long windowMillis;
    private final int maxKeys;
    private final Clock clock;

    /** キーごとの失敗の時刻（古い順）。メソッドはすべて synchronized で扱う */
    private final Map<String, Deque<Long>> failures = new HashMap<>();

    public FailureLimiter(int maxFailures, Duration window, int maxKeys, Clock clock) {
        if (maxFailures < 1 || maxKeys < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("maxFailures, maxKeys and window must be positive");
        }
        this.maxFailures = maxFailures;
        this.windowMillis = window.toMillis();
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    /**
     * 試す前に呼ぶ。止まっていなければ1回分を失敗として数えて empty を返す。
     * 止まっていれば数えずに、もう一度試せるまでの残り時間を返す
     */
    public synchronized Optional<Duration> tryAcquire(String key) {
        long now = clock.millis();
        Deque<Long> times = failures.get(key);
        if (times != null) {
            dropExpired(times, now);
            if (times.size() >= maxFailures) {
                // 最も古い失敗が window を過ぎれば、もう一度試せる
                return Optional.of(Duration.ofMillis(times.peekFirst() + windowMillis - now));
            }
        } else {
            if (failures.size() >= maxKeys) {
                evictOne(now);
            }
            times = new ArrayDeque<>();
            failures.put(key, times);
        }
        times.addLast(now);
        return Optional.empty();
    }

    /** 失敗ではなかった（照合が成功した・照合しなかった）とき、{@link #tryAcquire} で数えた1回分を戻す */
    public synchronized void release(String key) {
        Deque<Long> times = failures.get(key);
        if (times == null) {
            return;
        }
        times.pollLast();
        dropExpired(times, clock.millis());
        if (times.isEmpty()) {
            failures.remove(key);
        }
    }

    /** キーの回数をすべて消す */
    public synchronized void reset(String key) {
        failures.remove(key);
    }

    /**
     * 前方が prefix に一致するキーの回数をすべて消す（ログインの①で、1つのメールアドレスのすべての IP アドレスの分を消すのに使う）。
     * すべてのキーを調べるが、キーの数には上限があり、呼ぶのはパスワードの再設定の完了のときだけ
     */
    public synchronized void resetKeysStartingWith(String prefix) {
        failures.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** 今持っているキーの数（テスト用） */
    synchronized int keyCount() {
        return failures.size();
    }

    private void dropExpired(Deque<Long> times, long now) {
        long from = now - windowMillis;
        while (!times.isEmpty() && times.peekFirst() <= from) {
            times.pollFirst();
        }
    }

    /** 上限に達したときに1つ捨てる（順はクラスの説明のとおり） */
    private void evictOne(long now) {
        boolean droppedExpired = false;
        String oldestCounting = null;
        long oldestCountingLast = Long.MAX_VALUE;
        String soonestBlocked = null;
        long soonestBlockedFirst = Long.MAX_VALUE;
        for (Iterator<Map.Entry<String, Deque<Long>>> it = failures.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> times = entry.getValue();
            dropExpired(times, now);
            if (times.isEmpty()) {
                it.remove();
                droppedExpired = true;
            } else if (times.size() < maxFailures) {
                if (times.peekLast() < oldestCountingLast) {
                    oldestCountingLast = times.peekLast();
                    oldestCounting = entry.getKey();
                }
            } else if (times.peekFirst() < soonestBlockedFirst) {
                soonestBlockedFirst = times.peekFirst();
                soonestBlocked = entry.getKey();
            }
        }
        if (droppedExpired) {
            return;
        }
        failures.remove(oldestCounting != null ? oldestCounting : soonestBlocked);
    }
}
