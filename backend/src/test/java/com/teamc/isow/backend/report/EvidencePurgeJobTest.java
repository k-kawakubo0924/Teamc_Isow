package com.teamc.isow.backend.report;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * 定期実行（EvidencePurgeJob）の確認。今の時刻は Clock から読み、EvidencePurger に渡す（テストで差し替えられる）。
 * 処理の中で予期しない失敗があっても、例外を外に投げず（次の日の実行を止めない）、ログに残すだけにする
 */
class EvidencePurgeJobTest {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @Test
    void 今の時刻をClockから読んで消す処理に渡す() {
        EvidencePurger purger = mock(EvidencePurger.class);
        when(purger.purgeExpired(any())).thenReturn(new EvidencePurger.Result(0, 0));
        Clock fixed = Clock.fixed(Instant.parse("2027-06-01T18:00:00Z"), TOKYO);

        new EvidencePurgeJob(purger, fixed).run();

        verify(purger).purgeExpired(LocalDateTime.of(2027, 6, 2, 3, 0));
    }

    @Test
    void 予期しない失敗でも例外を外に投げない() {
        EvidencePurger purger = mock(EvidencePurger.class);
        when(purger.purgeExpired(any())).thenThrow(new IllegalStateException("DB に接続できない（テスト）"));

        assertThatCode(() -> new EvidencePurgeJob(purger, Clock.system(TOKYO)).run()).doesNotThrowAnyException();
    }
}
