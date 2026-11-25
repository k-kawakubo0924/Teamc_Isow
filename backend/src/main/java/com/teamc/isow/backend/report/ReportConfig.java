package com.teamc.isow.backend.report;

import com.teamc.isow.backend.image.ImageProperties;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通報の設定（docs/admin.md「通報」）。証拠の保存先と、証拠の保存期間を過ぎたものを消す定期実行（EvidencePurgeJob）。
 * 定期実行はこのアプリで最初のもの。今の時刻は Clock から読み、テストで差し替えられるようにする
 */
@Configuration
@EnableScheduling
public class ReportConfig {

    /** 証拠の保存先。公開の画像の保存先（app.image.local.dir）の外にあることを確かめる */
    @Bean
    public EvidenceStorage evidenceStorage(@Value("${app.evidence.local.dir}") Path evidenceDir, ImageProperties images) {
        return new LocalEvidenceStorage(evidenceDir, images.local().dir());
    }

    /** 今の時刻（日本時間。アプリの時計と同じ）。テストでは固定した Clock に差し替えられる */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
