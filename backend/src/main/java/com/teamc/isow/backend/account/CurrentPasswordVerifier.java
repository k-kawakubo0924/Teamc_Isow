package com.teamc.isow.backend.account;

import com.teamc.isow.backend.common.FailureLimiter;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.user.User;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * メールアドレスの変更・ログイン中のパスワードの変更で、現在のパスワードを確かめる
 * （docs/auth.md「ログインの失敗の回数の上限」の③）。
 *
 * <ul>
 *   <li>失敗はユーザーごとに、15分に5回まで。2か所で<b>合わせて</b>数える（分けると、交互に試すだけで2倍試せる）。メモリの上だけで数える</li>
 *   <li>止まっている間は、照合せずに 429（CurrentPasswordLimitExceededException。正しいパスワードでも通さない）</li>
 *   <li>成功したら回数を消す（本人のユーザーごとに数えるので、ほかの人の成功で戻されることがないため。ログインの②とは違う）</li>
 *   <li>上限に達しても、ログアウトはさせず、メールでも知らせない（理由は docs/auth.md）</li>
 * </ul>
 */
@Component
public class CurrentPasswordVerifier {

    /** 回数を数える長さ */
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final PasswordEncoder passwordEncoder;
    private final FailureLimiter perUser;

    public CurrentPasswordVerifier(PasswordEncoder passwordEncoder, Clock clock,
            @Value("${app.auth.current-password.max-failures-per-user:5}") int maxPerUser,
            @Value("${app.auth.current-password.max-tracked-users:10000}") int maxTrackedUsers) {
        this.passwordEncoder = passwordEncoder;
        this.perUser = new FailureLimiter(maxPerUser, WINDOW, maxTrackedUsers, clock);
    }

    /**
     * 現在のパスワードを確かめる。違えば 400（currentPassword）、失敗が続いていれば照合せずに 429。
     * 照合の前に1回分を先に数える（同時に送られても上限を超えて照合させないため。FailureLimiter）
     */
    public void verify(User user, String currentPassword) {
        String key = String.valueOf(user.getId());
        Optional<Duration> wait = perUser.tryAcquire(key);
        if (wait.isPresent()) {
            throw new CurrentPasswordLimitExceededException(wait.get());
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw InputValidationException.of("currentPassword", "現在のパスワードが正しくありません");
        }
        perUser.reset(key);
    }
}
