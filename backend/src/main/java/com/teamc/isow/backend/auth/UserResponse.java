package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.user.User;

/** ユーザー情報の応答（新規会員登録・ログイン中ユーザー取得で共通）。パスワード（ハッシュ含む）は返さない */
public record UserResponse(Long id, String email, String phoneNumber, String username) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getPhoneNumber(), user.getUsername());
    }
}
