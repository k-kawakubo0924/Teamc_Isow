package com.teamc.isow.backend.account;

import com.teamc.isow.backend.auth.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** メールアドレスの変更・ログイン中のパスワードの変更（docs/settings.md） */
@RestController
public class AccountController {

    private final EmailChangeService emailChangeService;
    private final PasswordChangeService passwordChangeService;

    public AccountController(EmailChangeService emailChangeService, PasswordChangeService passwordChangeService) {
        this.emailChangeService = emailChangeService;
        this.passwordChangeService = passwordChangeService;
    }

    /** ログイン中にパスワードを変える。ほかの端末はログアウトになり、この端末用の新しいトークンを返す */
    @PostMapping("/api/users/me/password")
    public LoginResponse changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountRequests.PasswordChange request) {
        return passwordChangeService.change(jwt.getSubject(), request);
    }

    /** 変更を申し込む（ログインが必要）。新しいアドレスがすでに使われていても同じ 202 */
    @PostMapping("/api/users/me/email-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountRequests.Accepted requestEmailChange(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountRequests.EmailChange request) {
        return emailChangeService.request(jwt.getSubject(), request);
    }

    /** 確認のリンクの鍵で変更を完了する。トークンなしで呼べる（SecurityConfig で許可）。鍵が使えなければ 400 */
    @PostMapping("/api/auth/email-change/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody AccountRequests.EmailChangeConfirm request) {
        emailChangeService.confirm(request.token());
    }
}
