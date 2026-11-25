package com.teamc.isow.backend.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * メールを送り出す窓口（docs/auth.md「メールの送信」）。メールを送るときは、必ずここを通す。
 *
 * <ul>
 *   <li>トランザクションの中で頼まれたら、<b>コミットした後に</b>送る（取り消された処理のメールは送らない）</li>
 *   <li>別のスレッドで送る（app.mail.async。既定は true）。応答を待たせないため、
 *       パスワードの再設定で「登録されているときだけ送るぶん遅い」という差が出ない</li>
 *   <li>送れなくても、頼んだ側に例外を投げない（利用者への応答は変えない）。アプリのログに失敗を残す</li>
 *   <li><b>ログには種類・ユーザー ID・成功か失敗かだけを書く。宛先・本文（鍵・リンク）は書かない</b>。
 *       送信の例外の message には宛先が入ることがあるため、例外は種類（クラス名）だけを書き、スタックトレースも出さない</li>
 *   <li>管理操作のログ（admin_operation_logs）には残さない（管理者の操作ではないため）</li>
 * </ul>
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final AppMailSender mailSender;
    private final TaskExecutor mailExecutor;

    public MailDispatcher(AppMailSender mailSender, TaskExecutor mailExecutor) {
        this.mailSender = mailSender;
        this.mailExecutor = mailExecutor;
    }

    /**
     * メールを送る。トランザクションの中ならコミットの後に、外ならすぐに送る
     *
     * @param userId ログに書くユーザー ID（宛先の代わり）
     */
    public void dispatch(MailKind kind, Long userId, String to, String subject, String body) {
        OutgoingMail mail = new OutgoingMail(to, subject, body);
        Runnable send = () -> mailExecutor.execute(() -> sendQuietly(kind, userId, mail));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void sendQuietly(MailKind kind, Long userId, OutgoingMail mail) {
        try {
            mailSender.send(mail);
            log.info("メールを送りました: kind={}, userId={}", kind, userId);
        } catch (RuntimeException e) {
            // 例外の message・スタックトレースには宛先が入ることがあるため、種類だけを書く
            log.warn("メールを送れませんでした: kind={}, userId={}, error={}", kind, userId, e.getClass().getSimpleName());
        }
    }
}
