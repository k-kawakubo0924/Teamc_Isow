package com.teamc.isow.backend.auth;

/**
 * メールのリンクの鍵が使えない（期限切れ・使用済み・壊れている・用途が違う）。400 で、message をそのまま画面に出す。
 * どの理由かは区別して返さない（鍵を調べる手がかりにさせないため）
 */
public class InvalidAccountLinkException extends RuntimeException {

    public InvalidAccountLinkException(String message) {
        super(message);
    }
}
