package com.teamc.isow.backend.moderation;

import com.teamc.isow.backend.auth.JwtTokenService;
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
 *   <li>トークンの世代（ver）が、DB のユーザーの世代と違えば 401（本文なし。期限切れと同じ扱い）にする。利用停止より先に確かめる
 *       （docs/auth.md「トークンの世代（全端末のログアウト）」）。世代の入っていないトークン（この仕組みの前に発行したもの）は世代0として扱う。
 *       毎回ユーザーを読むここで確かめるため、API ごとに確かめを書く必要はない</li>
 *   <li>例外として通す API はない。利用停止中の申し立て（/api/contacts/appeal）はトークンを付けずに、申し立て用の鍵を本文で送る
 *       （docs/settings.md「お問い合わせ」）。403 の応答で、その鍵を渡す</li>
 * </ul>
 * Spring の部品（@Component）にしない（すると、Spring Security の外でも二重に動くため）。SecurityConfig で組み込む
 */
public class SuspendedUserFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final SuspensionAppealTokens appealTokens;

    public SuspendedUserFilter(UserRepository userRepository, Clock clock, JsonMapper jsonMapper,
            SuspensionAppealTokens appealTokens) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
        this.appealTokens = appealTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<User> user = currentUser();
        if (user.isPresent() && tokenVersion() != user.get().getTokenVersion()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Optional<User> suspended = user.filter(u -> u.isSuspendedAt(LocalDateTime.now(clock)));
        if (suspended.isPresent()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // 有効なトークンを持つ本人なので、申し立て用の鍵も渡す（docs/settings.md「お問い合わせ」）
            response.getWriter().write(jsonMapper.writeValueAsString(
                    SuspendedErrorResponse.of(suspended.get(), appealTokens.issue(suspended.get()))));
            return;
        }
        chain.doFilter(request, response);
    }

    /** トークンの世代。入っていなければ 0 */
    private static long tokenVersion() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return 0;
        }
        Object version = jwt.getToken().getClaims().get(JwtTokenService.VERSION_CLAIM);
        return version instanceof Number number ? number.longValue() : 0;
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
