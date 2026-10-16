package com.teamc.isow.backend.mail;

/**
 * メールを1通送る（docs/auth.md「メールの送信」）。アプリでは SMTP（SmtpMailSender）、テストではメモリに貯める部品に差し替える。
 * 直接呼ばず、MailDispatcher を通すこと（コミットの後に送る・失敗しても投げない・ログに宛先を書かない）
 */
public interface AppMailSender {

    void send(OutgoingMail mail);
}
