package com.teamc.isow.backend.dm;

import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 30日やり取りがない相談を、1時間ごとに終了する定期実行（docs/dm.md「30日やり取りがない相談の自動終了」）。
 * 作りは通報の証拠を消す定期実行（EvidencePurgeJob）と同じ。
 *
 * <ul>
 *   <li>実行する時刻は app.dm.inactive-close-cron（既定は毎時15分）。テストでは「-」にして動かさない</li>
 *   <li>1時間ごとにするのは、終了が最大1時間遅れる程度で済ませ、受けられる相談の枠（進行中3件）を早く空けるため（SQL は軽い）</li>
 *   <li>今の時刻は Clock から読み、InactiveConversationCloser に渡す（テストで差し替えられる）</li>
 *   <li>予期しない失敗でも例外を外に投げない（次の実行を止めないため）。エラーのログに残す</li>
 * </ul>
 */
@Component
public class InactiveConversationJob {

    private static final Logger log = LoggerFactory.getLogger(InactiveConversationJob.class);

    private final InactiveConversationCloser closer;
    private final Clock clock;

    public InactiveConversationJob(InactiveConversationCloser closer, Clock clock) {
        this.closer = closer;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.dm.inactive-close-cron:0 15 * * * *}")
    public void run() {
        try {
            closer.closeInactive(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("30日やり取りがない相談を終了する定期実行に失敗しました（次の実行でやり直します）", e);
        }
    }
}
