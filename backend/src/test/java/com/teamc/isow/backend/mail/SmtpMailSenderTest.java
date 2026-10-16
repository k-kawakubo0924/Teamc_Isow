package com.teamc.isow.backend.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP での送信（SmtpMailSender）。開発中は Mailpit、本番は環境変数で指定した SMTP サーバーへ送る。
 * 文字だけのメール（HTML にしない）で、差出人は設定（app.mail.from）の値
 */
class SmtpMailSenderTest {

    @Test
    void 差出人_宛先_件名_本文を文字だけのメールにして送る() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        SmtpMailSender sender = new SmtpMailSender(javaMailSender, new MailProperties("no-reply@isho.test", "http://localhost:5173", true));

        sender.send(new OutgoingMail("to@example.com", "件名", "本文\n2行目"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@isho.test");
        assertThat(message.getTo()).containsExactly("to@example.com");
        assertThat(message.getSubject()).isEqualTo("件名");
        assertThat(message.getText()).isEqualTo("本文\n2行目");
    }
}
