package com.teamc.isow.backend.common;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * メモリの上だけで回数を数える制限（キーごとに、window の間に max 回まで）。
 *
 * <p><b>DB にもアプリのログにも保存しない</b>。サーバーを再起動すれば消え、window を過ぎた記録はその都度捨てる。
 * IP アドレスをキーにしてよいのは、この「保存しない・数分で消える」使い方のときだけ
 * （「IP アドレスは残さない」の解釈。docs/settings.md「いたずら・送りすぎを防ぐ方法」）。
 * キー（IP アドレスなど）をログ・例外の message に書かないこと。
 *
 * <p><b>キーの数には上限（maxKeys）がある</b>。IPv6 なら攻撃者が使えるアドレスは大量にあり、プロキシの後ろに置けば
 * ヘッダー由来の値がキーになるため、キーは増え続けうる。上限に達したら、新しいキーを数えられるよう、次の順で1つ捨てる
 * （{@link FailureLimiter} と同じ考え方。新しいキーを数えずに通すことはしない）。
 * <ol>
 *   <li>window を過ぎた記録しかないキー</li>
 *   <li>止まっていないキーのうち、最後の記録が最も古いもの</li>
 *   <li>すべて止まっているときだけ、止まり終わりが最も近いもの</li>
 * </ol>
 *
 * <p>失敗の回数を数える {@link FailureLimiter} と似ているが、まとめていない（docs/auth.md「メモリの上で数える部品が2つある」）
 */
public class InMemoryRateLimiter {

    private final int max;
    private final long windowMillis;
    private final int maxKeys;
    private final Clock clock;

    /** キーごとの記録の時刻（古い順）。メソッドはすべて synchronized で扱う */
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public InMemoryRateLimiter(int max, Duration window, int maxKeys, Clock clock) {
        if (max < 1 || maxKeys < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("max, maxKeys and window must be positive");
        }
        this.max = max;
        this.windowMillis = window.toMillis();
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    /** 1回分を数える。上限内なら true、超えていれば false（超えた分は数えない） */
    public synchronized boolean tryAcquire(String key) {
        long now = clock.millis();
        Deque<Long> times = hits.get(key);
        if (times != null) {
            dropExpired(times, now);
            if (times.size() >= max) {
                return false;
            }
        } else {
            if (hits.size() >= maxKeys) {
                evictOne(now);
            }
            times = new ArrayDeque<>();
            hits.put(key, times);
        }
        times.addLast(now);
        return true;
    }

    /** 今持っているキーの数（テスト用） */
    synchronized int keyCount() {
        return hits.size();
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
        for (Iterator<Map.Entry<String, Deque<Long>>> it = hits.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> times = entry.getValue();
            dropExpired(times, now);
            if (times.isEmpty()) {
                it.remove();
                droppedExpired = true;
            } else if (times.size() < max) {
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
        hits.remove(oldestCounting != null ? oldestCounting : soonestBlocked);
    }
}
