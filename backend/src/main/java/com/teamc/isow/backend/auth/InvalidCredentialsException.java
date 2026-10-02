package com.teamc.isow.backend.auth;

/**
 * ログイン失敗。メールアドレスが存在しない場合とパスワードが違う場合を区別しない
 * （どちらかを応答から推測できないようにするため）。
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("invalid credentials");
    }
}
