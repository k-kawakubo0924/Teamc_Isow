package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.user.User;

/** 新規会員登録の結果。パスワード（ハッシュ含む）は返さない */
public record RegisterResponse(Long id, String email, String phoneNumber, String username) {

    public static RegisterResponse from(User user) {
        return new RegisterResponse(user.getId(), user.getEmail(), user.getPhoneNumber(), user.getUsername());
    }
}
