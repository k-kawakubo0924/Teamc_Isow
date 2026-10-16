package com.teamc.isow.backend.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * メールの設定（application.properties の app.mail.*。docs/auth.md「メールの送信」）。
 * SMTP サーバーの接続先は Spring の spring.mail.*（MAIL_HOST など）で指定する
 *
 * @param from 差出人のアドレス（MAIL_FROM）
 * @param linkBaseUrl メールのリンクの土台の URL（FRONTEND_ORIGIN）。<b>リクエストの Host ヘッダーからは作らない</b>
 *     （ヘッダーを偽装されると、攻撃者のサイトへのリンク入りのメールを送らされるため）
 * @param async コミットの後に別のスレッドで送るか（既定は true。テストでは false にして、送ったものをすぐに確かめる）
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String from, String linkBaseUrl, boolean async) {

    /** 画面の URL（path は / から始める）。末尾の / の重なりを除く */
    public String link(String path) {
        String base = linkBaseUrl.endsWith("/") ? linkBaseUrl.substring(0, linkBaseUrl.length() - 1) : linkBaseUrl;
        return base + path;
    }
}
