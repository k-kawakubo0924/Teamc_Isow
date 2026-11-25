package com.teamc.isow.backend.report;

import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 証拠の保存期間を過ぎたものを、毎日消す定期実行（docs/admin.md「通報」）。このアプリで最初の定期実行。
 *
 * <ul>
 *   <li>実行する時刻は app.report.evidence-purge-cron（既定は毎日 3:00）。テストでは「-」にして動かさない</li>
 *   <li>今の時刻は Clock から読み、EvidencePurger に渡す（テストで差し替えられる）</li>
 *   <li>予期しない失敗でも例外を外に投げない（翌日の実行を止めないため）。エラーのログに残す</li>
 *   <li>サーバーを複数台で動かす場合は、同じ処理が同時に動く。まとまりの行をロックしてから消すため壊れはしないが、
 *       そのときは1台だけで動かす仕組み（ShedLock など）を検討する</li>
 * </ul>
 */
@Component
public class EvidencePurgeJob {

    private static final Logger log = LoggerFactory.getLogger(EvidencePurgeJob.class);

    private final EvidencePurger purger;
    private final Clock clock;

    public EvidencePurgeJob(EvidencePurger purger, Clock clock) {
        this.purger = purger;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.report.evidence-purge-cron:0 0 3 * * *}")
    public void run() {
        try {
            purger.purgeExpired(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("保存期間を過ぎた通報の証拠を消す定期実行に失敗しました（翌日にやり直します）", e);
        }
    }
}
