package com.teamc.isow.backend.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP でメールを送る（docs/auth.md「メールの送信」）。接続先は spring.mail.*（環境変数 MAIL_HOST・MAIL_PORT など）。
 * 開発中は docker-compose の Mailpit（localhost:1025）に送り、ブラウザ（http://localhost:8025）で見る。
 * 本番は環境変数を実際の SMTP サーバーに差し替えるだけで切り替わる
 */
@Component
public class SmtpMailSender implements AppMailSender {

    private final JavaMailSender javaMailSender;
    private final MailProperties properties;

    public SmtpMailSender(JavaMailSender javaMailSender, MailProperties properties) {
        this.javaMailSender = javaMailSender;
        this.properties = properties;
    }

    /** 文字だけのメールにして送る（HTML にはしない） */
    @Override
    public void send(OutgoingMail mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());
        javaMailSender.send(message);
    }
}
