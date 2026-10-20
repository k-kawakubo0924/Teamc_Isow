package com.teamc.isow.backend.account;

import com.teamc.isow.backend.auth.AccountMails;
import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.auth.JwtTokenService;
import com.teamc.isow.backend.auth.LoginResponse;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン中のパスワードの変更（docs/settings.md「パスワード再設定（ログイン中の変更）」、design/ResetPassword.png）。
 *
 * <ul>
 *   <li>現在のパスワードを求める（メールアドレスの変更とそろえる。開いたままの端末を触られただけで変えられないように）。
 *       失敗の回数はメールアドレスの変更と合わせて数える（CurrentPasswordVerifier）</li>
 *   <li>変更するとトークンの世代を進め、すべての端末をログアウトさせる。操作した端末だけは続けて使えるよう、新しいトークンを返す</li>
 *   <li>登録アドレスに「パスワードが変更されました」と知らせる</li>
 * </ul>
 */
@Service
public class PasswordChangeService {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final JwtTokenService jwtTokenService;
    private final AccountMails accountMails;
    private final Clock clock;

    public PasswordChangeService(AuthService authService, UserRepository userRepository, PasswordEncoder passwordEncoder,
            CurrentPasswordVerifier currentPasswordVerifier, JwtTokenService jwtTokenService, AccountMails accountMails,
            Clock clock) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentPasswordVerifier = currentPasswordVerifier;
        this.jwtTokenService = jwtTokenService;
        this.accountMails = accountMails;
        this.clock = clock;
    }

    /** パスワードを変え、この端末用の新しいトークンを返す。入力の誤りは 400 */
    @Transactional
    public LoginResponse change(String subject, AccountRequests.PasswordChange request) {
        User user = userRepository.findByIdForUpdate(authService.requireCurrentUser(subject).getId()).orElseThrow();
        currentPasswordVerifier.verify(user, request.currentPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw InputValidationException.of("newPassword", "今のパスワードと同じです");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        accountMails.passwordChanged(user, LocalDateTime.now(clock));
        // 進めた後の世代でトークンを発行する（この端末だけはログインしたままにする）
        return jwtTokenService.issue(user);
    }
}
