package com.teamc.isow.backend.auth;

/** トークンは正しいが、sub のユーザーが存在しない場合に投げる（退会後のトークンなど）。未認証(401)として扱う */
public class UnknownTokenUserException extends RuntimeException {

    public UnknownTokenUserException() {
        super("user in token not found");
    }
}
