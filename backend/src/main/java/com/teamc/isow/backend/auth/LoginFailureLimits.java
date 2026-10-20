package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.common.FailureLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ログインの失敗の回数の上限（docs/auth.md「ログインの失敗の回数の上限」）。メモリの上だけで数える。
 *
 * <ul>
 *   <li>① メールアドレス × IP アドレスごとに、15分に5回まで。止まるのは失敗した IP アドレスからだけなので、
 *       他人のアドレスで失敗し続けても、本人は別の IP アドレスからログインできる（締め出しに使われない）</li>
 *   <li>② IP アドレスごとに（アドレスを問わず）、15分に30回まで。多くのアドレスにまとめて試す攻撃を止める</li>
 *   <li>登録のないアドレスも、登録があるアドレスと同じように数えて止める（止まるかどうかで登録の有無が分からないように）</li>
 * </ul>
 *
 * <p><b>成功したときの扱いが①と②で違う。そろえないこと。</b>
 * <ul>
 *   <li>①は成功したら消す（本人が打ち間違えた後に入れたなら、その組み合わせで数え続ける理由がない）</li>
 *   <li>②は成功しても消さない。成功した1回を数えないだけ（release）。消すと、アカウントを1つ持っている攻撃者が、
 *       自分のアカウントにログインするだけで IP アドレスの残り回数を戻せる（29回失敗 → 自分にログイン → また29回失敗 … が続く）。
 *       成功した1回まで数えないのは、同じ IP アドレスを使う人たち（学校・会社など）が普通にログインするだけで止まらないように</li>
 * </ul>
 * どちらも LoginLimitApiTest で固定している
 */
@Component
public class LoginFailureLimits {

    /** 回数を数える長さ */
    static final Duration WINDOW = Duration.ofMinutes(15);

    /** ① メールアドレス × IP アドレス */
    private final FailureLimiter perAccountAndIp;

    /** ② IP アドレス */
    private final FailureLimiter perIp;

    public LoginFailureLimits(Clock clock,
            @Value("${app.auth.login.max-failures-per-account-and-ip:5}") int maxPerAccountAndIp,
            @Value("${app.auth.login.max-failures-per-ip:30}") int maxPerIp,
            @Value("${app.auth.login.max-tracked-keys:10000}") int maxTrackedKeys) {
        this.perAccountAndIp = new FailureLimiter(maxPerAccountAndIp, WINDOW, maxTrackedKeys, clock);
        this.perIp = new FailureLimiter(maxPerIp, WINDOW, maxTrackedKeys, clock);
    }

    /**
     * パスワードを照合する<b>前</b>に呼ぶ。止まっていなければ、この1回を失敗として先に数える
     * （照合の間に同時に送られた分まで通さないため。FailureLimiter）。
     * 止まっていれば LoginLimitExceededException（照合はさせない）
     *
     * @param email 正規化済みのメールアドレス
     * @param clientAddress 接続元の IP アドレス（メモリの上の回数制限だけに使い、保存もログへの記録もしない）
     */
    public void acquire(String email, String clientAddress) {
        Optional<Duration> ipWait = perIp.tryAcquire(clientAddress);
        if (ipWait.isPresent()) {
            throw new LoginLimitExceededException(ipWait.get());
        }
        Optional<Duration> accountWait = perAccountAndIp.tryAcquire(accountKey(email, clientAddress));
        if (accountWait.isPresent()) {
            // 照合しないので、②に先に数えた1回は戻す
            perIp.release(clientAddress);
            throw new LoginLimitExceededException(accountWait.get());
        }
    }

    /** パスワードが正しかった。①は消し、②は成功した1回を戻すだけ（消さない。理由はクラスの説明） */
    public void succeeded(String email, String clientAddress) {
        perAccountAndIp.reset(accountKey(email, clientAddress));
        perIp.release(clientAddress);
    }

    /**
     * パスワードの再設定が完了した。そのアドレスの①を、すべての IP アドレスの分まとめて消す。
     * 再設定を完了できるのはメールボックスを持つ本人なので、消しても攻撃者に何も渡さない（できたてのパスワードで締め出さない）。
     * <b>②は消さない</b>（消すと、メールボックスを1つ持っている攻撃者が、再設定をするだけで IP アドレスの残り回数を戻せる。
     * 成功したときに②を消さないのと同じ理由。クラスの説明）
     *
     * @param email 正規化済みのメールアドレス（users に保存しているもの。ログインのときと同じ形）
     */
    public void passwordReset(String email) {
        perAccountAndIp.resetKeysStartingWith(email + '\0');
    }

    /** メールアドレスには使えない文字（NUL）で区切り、別の組み合わせが同じキーにならないようにする */
    private static String accountKey(String email, String clientAddress) {
        return email + '\0' + clientAddress;
    }
}
