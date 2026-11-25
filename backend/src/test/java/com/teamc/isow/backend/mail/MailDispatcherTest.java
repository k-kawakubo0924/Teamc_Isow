package com.teamc.isow.backend.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * メールの送り出し（MailDispatcher。docs/auth.md「メールの送信」）。
 *
 * <ul>
 *   <li>トランザクションの中で頼まれたら、コミットした後に送る（取り消されたら送らない）</li>
 *   <li>送れなくても、頼んだ側に例外を投げない（利用者への応答は変えない）</li>
 *   <li>アプリのログには種類・ユーザー ID・成功か失敗かだけを書く。<b>宛先・本文（鍵・リンク）は書かない</b></li>
 * </ul>
 * テストでは同期で送る（app.mail.async=false）
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class MailDispatcherTest {

    private static final String ADDRESS = "mail-dispatch-secret@example.com";
    private static final String BODY = "本文の中の鍵 SECRET-TOKEN-123 と https://example.test/reset#token=SECRET-TOKEN-123";

    @Autowired
    private MailDispatcher dispatcher;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        mailSender.clear();
    }

    @Test
    void トランザクションの中で頼まれたらコミットした後に送り_取り消されたら送らない() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            dispatcher.dispatch(MailKind.PASSWORD_RESET, 1L, ADDRESS, "件名", BODY);
            assertThat(mailSender.sent()).isEmpty();
        });
        assertThat(mailSender.sent()).hasSize(1);

        tx.executeWithoutResult(status -> {
            dispatcher.dispatch(MailKind.PASSWORD_RESET, 1L, ADDRESS, "取り消される", BODY);
            status.setRollbackOnly();
        });
        assertThat(mailSender.sent()).hasSize(1);
    }

    @Test
    void トランザクションの外ではすぐに送る_差出人は設定の値() {
        dispatcher.dispatch(MailKind.PASSWORD_CHANGED, 2L, ADDRESS, "件名", BODY);

        assertThat(mailSender.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo(ADDRESS);
            assertThat(mail.subject()).isEqualTo("件名");
            assertThat(mail.body()).isEqualTo(BODY);
        });
    }

    @Test
    void 送れなくても例外を投げず_ログには種類とユーザーIDだけを書き_宛先と本文は書かない(CapturedOutput output) {
        mailSender.failNext(new MailSendException("Invalid Addresses: " + ADDRESS));

        assertThatCode(() -> dispatcher.dispatch(MailKind.PASSWORD_RESET, 3L, ADDRESS, "件名", BODY))
                .doesNotThrowAnyException();

        assertThat(output.getAll()).contains("PASSWORD_RESET").contains("userId=3");
        assertThat(output.getAll()).doesNotContain(ADDRESS, "SECRET-TOKEN-123", "example.test");
    }

    @Test
    void 送れたときもログに宛先と本文を書かない(CapturedOutput output) {
        dispatcher.dispatch(MailKind.PASSWORD_RESET, 4L, ADDRESS, "件名", BODY);

        assertThat(output.getAll()).contains("PASSWORD_RESET").contains("userId=4");
        assertThat(output.getAll()).doesNotContain(ADDRESS, "SECRET-TOKEN-123", "example.test");
    }
}
