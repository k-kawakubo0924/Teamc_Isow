package com.teamc.isow.backend.mail;

/** メールの種類（docs/auth.md「メールの送信」）。アプリのログには、宛先の代わりにこれとユーザー ID だけを書く */
public enum MailKind {

    /** パスワードの再設定のリンク */
    PASSWORD_RESET,
    /** パスワードが変更された（再設定・ログイン中の変更のどちらも） */
    PASSWORD_CHANGED,
    /** 新しいメールアドレスの確認のリンク */
    EMAIL_CHANGE_CONFIRM,
    /** 変更先に指定されたアドレスが、すでに別のアカウントで登録されている（そのアドレスの持ち主に知らせる） */
    EMAIL_ALREADY_REGISTERED,
    /** メールアドレスが変更された（古いアドレスに知らせる） */
    EMAIL_CHANGED
}
