package com.teamc.isow.backend.user;

/** 指定したユーザーが存在しない。404 として扱う */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("user not found: " + id);
    }
}
