package com.teamc.isow.backend.mail;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * テストで使うメールの送り先（docs/auth.md「メールの送信」）。SMTP には送らず、送ったメールをメモリに貯める。
 * テストのソースに置いた部品は、アプリの部品と一緒に読み込まれるため、すべての Spring のテストでこちらが使われる（@Primary）
 */
@Component
@Primary
public class RecordingMailSender implements AppMailSender {

    private final List<OutgoingMail> sent = new ArrayList<>();
    private RuntimeException nextFailure;

    @Override
    public synchronized void send(OutgoingMail mail) {
        if (nextFailure != null) {
            RuntimeException failure = nextFailure;
            nextFailure = null;
            throw failure;
        }
        sent.add(mail);
    }

    public synchronized List<OutgoingMail> sent() {
        return List.copyOf(sent);
    }

    /** 宛先が to のメール（古い順） */
    public synchronized List<OutgoingMail> sentTo(String to) {
        return sent.stream().filter(mail -> mail.to().equals(to)).toList();
    }

    /** 次の1通の送信を失敗させる */
    public synchronized void failNext(RuntimeException failure) {
        this.nextFailure = failure;
    }

    public synchronized void clear() {
        sent.clear();
        nextFailure = null;
    }

    /** 本文の中の、最初の URL（https?:// から空白・改行まで） */
    public static String firstLink(OutgoingMail mail) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(mail.body());
        if (!m.find()) {
            throw new AssertionError("本文にリンクがありません: " + mail.subject());
        }
        return m.group();
    }

    /** リンクの # の後ろの token の値 */
    public static String tokenOf(OutgoingMail mail) {
        String link = firstLink(mail);
        int at = link.indexOf("#token=");
        if (at < 0) {
            throw new AssertionError("リンクに #token= がありません");
        }
        return link.substring(at + "#token=".length());
    }
}
