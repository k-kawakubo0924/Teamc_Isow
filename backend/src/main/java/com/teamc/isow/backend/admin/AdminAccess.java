package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理 API のサービスで、管理者であることを確かめる（docs/admin.md「権限の仕組み」）。
 *
 * <p>/api/admin/** は SecurityConfig（AdminAuthorizationManager）で守っているが、設定の書き間違いで穴が開かないよう、
 * サービスでも確かめる（二重の守り）。管理者でなければ、SecurityConfig と同じく存在しない URL と同じ 404 にする
 * （ResponseStatusException は sendError で返るため、エラー画面を通り、本文の形も同じになる）。
 */
@Component
public class AdminAccess {

    private final AuthService authService;

    public AdminAccess(AuthService authService) {
        this.authService = authService;
    }

    /** ログイン中のユーザーが管理者なら返す。管理者でなければ 404 */
    public User requireAdmin(String subject) {
        User user = authService.requireCurrentUser(subject);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return user;
    }
}
