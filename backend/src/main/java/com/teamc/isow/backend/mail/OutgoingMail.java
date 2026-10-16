package com.teamc.isow.backend.mail;

/**
 * 送るメール1通（文字だけのメール。HTML にはしない）。docs/auth.md「メールの送信」。
 * 宛先・本文（リンク・鍵）は個人情報や秘密を含むため、ログ・例外の message に入れないこと
 */
public record OutgoingMail(String to, String subject, String body) {

    /** 中身をログに出さないよう、toString には何も書かない（record の既定の toString は全項目を出すため） */
    @Override
    public String toString() {
        return "OutgoingMail[hidden]";
    }
}
