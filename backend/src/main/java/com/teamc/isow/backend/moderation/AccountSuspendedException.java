package com.teamc.isow.backend.moderation;

import com.teamc.isow.backend.user.User;

/** 利用停止中のユーザーがログイン・API を使おうとした（403。応答は SuspendedErrorResponse） */
public class AccountSuspendedException extends RuntimeException {

    private final transient User user;

    public AccountSuspendedException(User user) {
        super("account suspended: " + user.getId());
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}
