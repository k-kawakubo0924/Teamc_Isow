package com.teamc.isow.backend.report;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 証拠の保存期間を過ぎたものを消す（docs/admin.md「通報」の「証拠の保存期間」）。毎日 EvidencePurgeJob から呼ばれる。
 *
 * <ul>
 *   <li>対象は、対応を終えて（対応済み・却下にして）から {@link #RETENTION}（180日）を過ぎた通報の証拠。未対応の通報の証拠は消さない</li>
 *   <li>消すファイルは、ReportEvidenceFile に記録した正確なファイル名だけ。<b>パターン・ワイルドカード・フォルダの一覧から探して消すことはしない</b></li>
 *   <li>文字のコピー（evidence_json）と一覧の要約も消す。通報の行（理由・状態・日時）とまとまりの状態は残す</li>
 *   <li>今の時刻は引数で受け取る（テストで差し替えられるように。定期実行では Clock から読む）</li>
 * </ul>
 *
 * <p>失敗したとき：
 * <ul>
 *   <li>ファイルを消せなかったら、そのファイルの記録（ReportEvidenceFile）を残し、そのまとまりは「消した」にしない
 *       （文字のコピーも残す）。翌日の実行で消し直す。警告のログに、まとまりの ID とファイル名を残す（内容・ユーザー名は書かない）</li>
 *   <li>1つのまとまりの失敗で、ほかのまとまりを止めない（まとまりごとに別のトランザクションにする）</li>
 *   <li>ファイルを先に消し、DB の記録は後で消す。DB の更新に失敗しても、翌日にもう一度ファイルを消しにいく（存在しなければ何もしない）ため、
 *       最終的にファイルも記録も消える</li>
 * </ul>
 */
@Component
public class EvidencePurger {

    private static final Logger log = LoggerFactory.getLogger(EvidencePurger.class);

    /**
     * 証拠の保存期間（対応を終えてから）。決定を見直す余地（通報した人・された人からの問い合わせ、判断のやり直し）を残しつつ、
     * 利用者が削除した内容をいつまでも持ち続けないための期間（docs/admin.md「通報」）
     */
    public static final Duration RETENTION = Duration.ofDays(180);

    private final ReportCaseRepository caseRepository;
    private final ReportEvidenceFileRepository evidenceFileRepository;
    private final EvidenceStorage evidenceStorage;
    private final TransactionTemplate transactionTemplate;

    public EvidencePurger(ReportCaseRepository caseRepository, ReportEvidenceFileRepository evidenceFileRepository,
            EvidenceStorage evidenceStorage, PlatformTransactionManager transactionManager) {
        this.caseRepository = caseRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceStorage = evidenceStorage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 実行の結果（消したまとまりの数・消せなかったファイルの数） */
    public record Result(int purgedCases, int failedFiles) {
    }

    /** now の時点で保存期間を過ぎた証拠を消す */
    public Result purgeExpired(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(RETENTION);
        List<Long> caseIds = caseRepository.findIdsToPurge(cutoff);
        int purged = 0;
        int failed = 0;
        for (Long caseId : caseIds) {
            try {
                Integer failedInCase = transactionTemplate.execute(status -> purgeCase(caseId, now));
                if (failedInCase != null && failedInCase == 0) {
                    purged++;
                } else if (failedInCase != null) {
                    failed += failedInCase;
                }
            } catch (RuntimeException e) {
                log.error("通報の証拠を消す処理に失敗しました（翌日にやり直します）: caseId={}", caseId, e);
            }
        }
        log.info("保存期間を過ぎた通報の証拠を消しました: 対象={}件, 消した={}件, 消せなかったファイル={}件",
                caseIds.size(), purged, failed);
        return new Result(purged, failed);
    }

    /** トランザクションの中で呼ぶ。消せなかったファイルの数を返す（0 なら、まとまりを「消した」にした） */
    private int purgeCase(Long caseId, LocalDateTime now) {
        ReportCase reportCase = caseRepository.findByIdForUpdate(caseId).orElse(null);
        if (reportCase == null || reportCase.isEvidencePurged() || reportCase.getClosedAt() == null) {
            return 0;
        }
        int failed = 0;
        for (ReportEvidenceFile file : evidenceFileRepository.findByReportCaseIdOrderBySortOrder(caseId)) {
            try {
                // 記録した正確なファイル名だけを消す
                evidenceStorage.delete(file.getFileName());
                evidenceFileRepository.delete(file);
            } catch (RuntimeException e) {
                failed++;
                log.warn("証拠のファイルを削除できませんでした（記録を残し、翌日に消し直します）: caseId={}, file={}",
                        caseId, file.getFileName(), e);
            }
        }
        if (failed == 0) {
            reportCase.purgeEvidence(now);
        }
        return failed;
    }
}
