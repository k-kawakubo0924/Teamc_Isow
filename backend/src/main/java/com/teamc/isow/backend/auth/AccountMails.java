package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.mail.MailDispatcher;
import com.teamc.isow.backend.mail.MailKind;
import com.teamc.isow.backend.mail.MailProperties;
import com.teamc.isow.backend.user.User;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * アカウントに関するメールの文面（docs/auth.md「メールの本文」）。
 *
 * <ul>
 *   <li>書くもの：アプリ名・用件・リンク・有効期限・心当たりがない場合のこと・@ユーザー名（どのアカウントの件か分かるように）</li>
 *   <li>書かないもの：電話番号・パスワード・ほかのメールアドレス</li>
 *   <li>文字だけのメール（HTML にしない）。リンクは画面のオリジン（FRONTEND_ORIGIN）から作り、鍵は URL の # の後ろに置く
 *       （# の後ろはサーバーに送られないため、アクセスログ・リンク元の情報に鍵が残らない）</li>
 * </ul>
 */
@Component
public class AccountMails {

    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm");
    private static final String FOOTER = """

            ISHO
            （このメールは送信専用です。返信はできません）
            """;

    private final MailDispatcher dispatcher;
    private final MailProperties properties;

    public AccountMails(MailDispatcher dispatcher, MailProperties properties) {
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    /** パスワードの再設定のリンク */
    public void passwordReset(User user, String token) {
        String link = properties.link("/reset-password#token=" + token);
        dispatcher.dispatch(MailKind.PASSWORD_RESET, user.getId(), user.getEmail(), "【ISHO】パスワードの再設定", """
                @%s さん

                パスワードの再設定を受け付けました。
                次のリンクを開いて、新しいパスワードを設定してください。
                リンクの有効期限は30分です。使えるのは1回だけです。

                %s

                心当たりがない場合は、このメールを無視してください。パスワードは変わりません。
                """.formatted(user.getUsername(), link) + FOOTER);
    }

    /**
     * パスワードが変更された（再設定・ログイン中の変更）。登録アドレスに送る。
     * 心当たりがない場合は、メールを受け取れる本人なら「パスワードをお忘れですか？」から取り戻せるため、それを案内する
     */
    public void passwordChanged(User user, LocalDateTime at) {
        dispatcher.dispatch(MailKind.PASSWORD_CHANGED, user.getId(), user.getEmail(), "【ISHO】パスワードが変更されました", """
                @%s さん

                ISHO のパスワードが変更されました（%s）。
                安全のため、すべての端末からログアウトしました。新しいパスワードでログインしてください。

                この変更に心当たりがない場合は、ログイン画面の「パスワードをお忘れですか？」から、
                すぐにパスワードを再設定してください。
                """.formatted(user.getUsername(), AT.format(at)) + FOOTER);
    }

    /** 新しいメールアドレスの確認のリンク（新しいアドレスに送る）。古いアドレスは書かない */
    public void emailChangeConfirm(User user, String newEmail, String token) {
        String link = properties.link("/email-change/confirm#token=" + token);
        dispatcher.dispatch(MailKind.EMAIL_CHANGE_CONFIRM, user.getId(), newEmail, "【ISHO】メールアドレスの確認", """
                @%s さん

                ISHO のメールアドレスを、このアドレスに変更する手続きを受け付けました。
                次のリンクを開くと、変更が完了します。リンクの有効期限は24時間です。

                %s

                変更が完了するまでは、今のメールアドレスでログインできます。
                心当たりがない場合は、このメールを無視してください。変更は行われません。
                """.formatted(user.getUsername(), link) + FOOTER);
    }

    /**
     * 変更先に指定されたアドレスが、すでに別のアカウントで登録されている（そのアドレスの持ち主に知らせる）。
     * 申し込んだ人には、ほかの場合と同じ応答を返す（他人の登録の有無が分からないように）。申し込んだアカウントのことは書かない
     */
    public void emailAlreadyRegistered(User owner) {
        dispatcher.dispatch(MailKind.EMAIL_ALREADY_REGISTERED, owner.getId(), owner.getEmail(),
                "【ISHO】メールアドレスの変更の手続きについて", """
                このメールアドレスで登録されている ISHO のアカウントがありますが、
                別のアカウントから、このメールアドレスへの変更の手続きがありました。

                このメールアドレスはすでに使われているため、変更は行っていません。
                あなたのアカウントには何も変わりはありません。心当たりがない場合は、このメールを無視してください。
                """ + FOOTER);
    }

    /**
     * メールアドレスが変更された（古いアドレスに送る。乗っ取られたときに本人が気づけるように）。新しいアドレスは書かない。
     *
     * <p><b>「運営にご連絡ください」とは案内しない</b>。乗っ取られた本人はログインできず、お問い合わせはログインが必要なため、
     * 実際には連絡する手段がない。手段がないのに案内するのは、案内しないより悪い。
     * 将来、このメールに「元に戻す」リンクを入れる（docs/settings.md「未確定・要確認」）
     */
    public void emailChanged(User user, String oldEmail, LocalDateTime at) {
        dispatcher.dispatch(MailKind.EMAIL_CHANGED, user.getId(), oldEmail, "【ISHO】メールアドレスが変更されました", """
                @%s さん

                ISHO のアカウントのメールアドレスが変更されました（%s）。
                今後、このメールアドレスではログインできません。このアドレスへのお知らせは、このメールが最後です。

                この変更に心当たりがない場合は、第三者にアカウントを使われた可能性があります。
                申し訳ありませんが、現在、このメールからアカウントを取り戻す手段はご用意できていません。
                同じパスワードをほかのサービスでも使っている場合は、そちらのパスワードを変更してください。
                """.formatted(user.getUsername(), AT.format(at)) + FOOTER);
    }
}
