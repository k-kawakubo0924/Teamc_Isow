package com.teamc.isow.backend.moderation;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 利用停止中のユーザーの、すべての API を止める（docs/admin.md「6. 措置」）。
 *
 * <ul>
 *   <li>トークンを確かめた後（BearerTokenAuthenticationFilter の後）に、トークンのユーザーを DB から読み、利用停止中なら 403 を返す。
 *       ここ1か所で止めるため、API ごとに確かめを書く必要はない（書き忘れが起きない）。VisibilityInventoryTest で、
 *       登録したすべての API が止まることを確かめる</li>
 *   <li>トークンは失効させない（失効の仕組みはない）。リクエストのたびに DB を読むため、利用停止にした直後から、
 *       すでに持っているトークンでも使えなくなる。期限を過ぎれば、その時点から使える</li>
 *   <li>ログインしていないリクエスト（ログイン・新規会員登録など）はそのまま通す。ログインの利用停止は AuthService で確かめる</li>
 * </ul>
 * Spring の部品（@Component）にしない（すると、Spring Security の外でも二重に動くため）。SecurityConfig で組み込む
 */
public class SuspendedUserFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    public SuspendedUserFilter(UserRepository userRepository, Clock clock, JsonMapper jsonMapper) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<User> suspended = currentUser().filter(user -> user.isSuspendedAt(LocalDateTime.now(clock)));
        if (suspended.isPresent()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(jsonMapper.writeValueAsString(SuspendedErrorResponse.of(suspended.get())));
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return Optional.empty();
        }
        try {
            return userRepository.findById(Long.valueOf(jwt.getToken().getSubject()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
