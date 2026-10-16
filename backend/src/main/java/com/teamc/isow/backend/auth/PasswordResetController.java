package com.teamc.isow.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * パスワードの再設定（ログイン前。docs/auth.md「パスワードの再設定」）。どちらもトークンなしで呼べる（SecurityConfig で許可）
 */
@RestController
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** 再設定のメールを頼む。登録の有無にかかわらず、いつも 202 と同じ文言（形式の誤りだけは 400） */
    @PostMapping("/api/auth/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetRequests.Accepted request(@Valid @RequestBody PasswordResetRequests.Request request,
            HttpServletRequest httpRequest) {
        return passwordResetService.request(request.email(), httpRequest.getRemoteAddr());
    }

    /** メールのリンクの鍵で、新しいパスワードを設定する。鍵が使えなければ 400 */
    @PostMapping("/api/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody PasswordResetRequests.Confirm request) {
        passwordResetService.confirm(request.token(), request.newPassword());
    }
}
