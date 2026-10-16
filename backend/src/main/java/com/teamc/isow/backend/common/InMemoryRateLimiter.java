package com.teamc.isow.backend.common;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * メモリの上だけで回数を数える制限（キーごとに、window の間に max 回まで）。
 *
 * <p><b>DB にもアプリのログにも保存しない</b>。サーバーを再起動すれば消え、window を過ぎた記録はその都度捨てる。
 * IP アドレスをキーにしてよいのは、この「保存しない・数分で消える」使い方のときだけ
 * （「IP アドレスは残さない」の解釈。docs/settings.md「いたずら・送りすぎを防ぐ方法」）。
 * キー（IP アドレスなど）をログ・例外の message に書かないこと
 */
public class InMemoryRateLimiter {

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    /** 1回分を数える。上限内なら true、超えていれば false（超えた分は数えない） */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        long from = now - window.toMillis();
        boolean[] allowed = {false};
        hits.compute(key, (k, times) -> {
            Deque<Long> deque = times == null ? new ArrayDeque<>() : times;
            while (!deque.isEmpty() && deque.peekFirst() <= from) {
                deque.pollFirst();
            }
            if (deque.size() < max) {
                deque.addLast(now);
                allowed[0] = true;
            }
            // 空になったキーは消す（記録を残し続けないため）
            return deque.isEmpty() ? null : deque;
        });
        return allowed[0];
    }
}
