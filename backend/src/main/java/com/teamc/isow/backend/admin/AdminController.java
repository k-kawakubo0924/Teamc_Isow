package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理者向けの API（docs/admin.md）。/api/admin/** は SecurityConfig で管理者だけに許可している。
 * 管理者でない人には、存在しない URL と同じ 404 を返す
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAccess adminAccess;
    private final AdminOperationLogQueryService operationLogQueryService;

    public AdminController(AdminAccess adminAccess, AdminOperationLogQueryService operationLogQueryService) {
        this.adminAccess = adminAccess;
        this.operationLogQueryService = operationLogQueryService;
    }

    /**
     * ログイン中のユーザーが管理者か（管理画面を開いたときに、画面側で確かめるために使う）。
     * 管理者なら 200、それ以外は 404。判定は守りと同じ処理のため、画面とサーバーの判定がずれない
     */
    @GetMapping("/me")
    public AdminMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        User admin = adminAccess.requireAdmin(jwt.getSubject());
        return new AdminMeResponse(admin.getId(), admin.getUsername());
    }

    /** 管理操作のログの一覧（新しい順）。size は 1〜50 に丸める */
    @GetMapping("/operation-logs")
    public AdminOperationLogListResponse operationLogs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operationLogQueryService.list(jwt.getSubject(), page, size);
    }

    /** 管理者の情報。メールアドレスなどは返さない */
    public record AdminMeResponse(Long id, String username) {
    }
}
