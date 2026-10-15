package com.teamc.isow.backend.dm;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 30日やり取りがない相談を自動で終了する（docs/dm.md「30日やり取りがない相談の自動終了」）。InactiveConversationJob から呼ばれる。
 *
 * <ul>
 *   <li>対象は進行中（最終メッセージ日時と承認日時の遅いほうから30日）と、申請中（申込日時から30日）</li>
 *   <li>終了した人は null、理由は INACTIVITY。通知は作らない（会話の終了は通知しない。docs/dm.md「通知」）</li>
 *   <li>今の時刻は引数で受け取る（定期実行では Clock から読む。テストで差し替えられる）。
 *       <b>DB の現在時刻とは比べない</b>（9時間ずれるため。docs/admin.md「時刻の扱い」）</li>
 *   <li>1件ずつ別のトランザクションで、行をロックしてから読み直し、まだ条件に合うときだけ終了する。
 *       そのため、2回動いても同じ相談を二重に終了せず、メッセージの送信（同じ行をロックする）と同時でも、
 *       ちょうど届いた相談は終了しない。1件の失敗で、ほかの相談を止めない</li>
 * </ul>
 */
@Component
public class InactiveConversationCloser {

    private static final Logger log = LoggerFactory.getLogger(InactiveConversationCloser.class);

    /** やり取りがないまま、この期間がたったら終了する。画面の文言（「30日間やり取りがなかったため…」）と合わせること */
    public static final Duration INACTIVE_PERIOD = Duration.ofDays(30);

    private final ConversationRepository conversationRepository;
    private final TransactionTemplate transactionTemplate;

    public InactiveConversationCloser(ConversationRepository conversationRepository,
            PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 実行の結果（終了した数・失敗した数） */
    public record Result(int closed, int failed) {
    }

    /** now の時点で30日やり取りがない相談を終了する */
    public Result closeInactive(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(INACTIVE_PERIOD);
        List<Long> ids = conversationRepository.findInactiveIds(cutoff, ConversationStatus.REQUESTED.name(),
                ConversationStatus.ACTIVE.name());
        int closed = 0;
        int failed = 0;
        for (Long id : ids) {
            try {
                Boolean ended = transactionTemplate.execute(status -> conversationRepository.findByIdForUpdate(id)
                        // 探した後に、メッセージが届いた・終了した・承認された相談は、読み直したときに条件から外れる
                        .filter(conversation -> conversation.isInactiveAsOf(cutoff))
                        .map(conversation -> {
                            conversation.endByInactivity(now);
                            return true;
                        })
                        .orElse(false));
                if (Boolean.TRUE.equals(ended)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("やり取りがない相談を終了できませんでした: conversationId={}", id, e);
            }
        }
        if (closed > 0 || failed > 0) {
            log.info("30日やり取りがない相談を終了しました: 終了={}, 失敗={}", closed, failed);
        }
        return new Result(closed, failed);
    }
}
