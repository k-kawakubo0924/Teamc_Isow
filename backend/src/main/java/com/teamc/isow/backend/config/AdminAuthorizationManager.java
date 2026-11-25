package com.teamc.isow.backend.config;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * /api/admin/** の認可（docs/admin.md「権限の仕組み」）。
 *
 * <p>リクエストのたびに、トークンの sub のユーザーを DB から読み、User.isAdmin()（role が ADMIN と一致するか）で判定する。
 * トークンには権限を入れない（中身は誰でも読めるうえ、入れると管理者から外しても有効期限まで使えてしまうため）。
 * ユーザーがいない・sub が数字でない・認証されていない場合は、管理者ではないとする。
 */
@Component
public class AdminAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final UserRepository userRepository;

    public AdminAuthorizationManager(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        return new AuthorizationDecision(isAdmin(authentication.get()));
    }

    private boolean isAdmin(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            return false;
        }
        try {
            return userRepository.findById(Long.valueOf(jwt.getToken().getSubject()))
                    .map(User::isAdmin)
                    .orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
