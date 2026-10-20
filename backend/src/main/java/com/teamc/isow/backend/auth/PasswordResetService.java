package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.common.InMemoryRateLimiter;
import com.teamc.isow.backend.config.JwtPurpose;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * パスワードの再設定（ログイン前。docs/auth.md「パスワードの再設定」）。
 *
 * <ul>
 *   <li><b>登録の有無にかかわらず、同じ応答を返す</b>（登録済みかどうかを外から調べられないように）。
 *       メールはコミットの後に別のスレッドで送るため、応答の速さにも差が出ない</li>
 *   <li>同じアカウントには {@link #PER_ACCOUNT_INTERVAL}（5分）に1通まで。IP アドレスごとの回数制限はメモリの上だけで数える
 *       （DB・アプリのログに保存しない）。どちらも、超えた分は同じ応答を返して送らないだけ</li>
 *   <li>リンクの鍵は purpose=password_reset の JWT（30分。AccountTokens）。トークンの世代を入れ、再設定で世代が進むので1回しか使えない</li>
 *   <li>再設定するとトークンの世代を進め、すべての端末をログアウトさせる。登録アドレスに「パスワードが変更されました」と知らせる</li>
 *   <li>再設定を完了すると、ログインの失敗の回数のうち、そのアドレスの分（①）を消す。IP アドレスごとの分（②）は消さない（LoginFailureLimits）</li>
 *   <li>利用停止中の人も再設定できる（ログインすると、今までどおり利用停止の画面と申し立てに進める）</li>
 *   <li>アプリのログに、メールアドレス・鍵・リンクを書かない（送ったことは MailDispatcher が種類とユーザー ID だけ書く）</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    /** リンクの有効期限 */
    static final Duration RESET_VALIDITY = Duration.ofMinutes(30);

    /** 同じアカウントに再設定のメールを送る間隔 */
    static final Duration PER_ACCOUNT_INTERVAL = Duration.ofMinutes(5);

    /** IP アドレスごとの回数を数える長さ */
    static final Duration PER_IP_WINDOW = Duration.ofMinutes(10);

    static final String ACCEPTED_MESSAGE =
            "入力したメールアドレスが登録されていれば、パスワード再設定のメールを送りました。届かない場合は、時間をおいてもう一度お試しください。";

    static final String INVALID_LINK_MESSAGE =
            "このリンクは使えません。有効期限（30分）が切れたか、すでに使われています。もう一度、再設定のメールを送ってください。";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountTokens accountTokens;
    private final AccountMails accountMails;
    private final Clock clock;
    private final InMemoryRateLimiter perIpLimiter;
    private final LoginFailureLimits loginFailureLimits;

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AccountTokens accountTokens, AccountMails accountMails, Clock clock, LoginFailureLimits loginFailureLimits,
            @Value("${app.auth.password-reset.max-requests-per-ip:10}") int maxRequestsPerIp,
            @Value("${app.auth.password-reset.max-tracked-ips:10000}") int maxTrackedIps) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountTokens = accountTokens;
        this.accountMails = accountMails;
        this.clock = clock;
        this.loginFailureLimits = loginFailureLimits;
        this.perIpLimiter = new InMemoryRateLimiter(maxRequestsPerIp, PER_IP_WINDOW, maxTrackedIps, clock);
    }

    /**
     * 再設定のメールを頼む。email は正規化・形式の確認済みであること。どの場合も同じ応答を返す
     *
     * @param clientAddress 接続元の IP アドレス（メモリの上の回数制限だけに使い、保存もログへの記録もしない）
     */
    @Transactional
    public PasswordResetRequests.Accepted request(String email, String clientAddress) {
        if (perIpLimiter.tryAcquire(clientAddress)) {
            LocalDateTime now = LocalDateTime.now(clock);
            userRepository.findByEmail(email)
                    .filter(user -> user.tryRecordPasswordResetRequest(now, PER_ACCOUNT_INTERVAL))
                    .ifPresent(user -> accountMails.passwordReset(user,
                            accountTokens.issue(JwtPurpose.PASSWORD_RESET, user, RESET_VALIDITY, Map.of())));
        }
        return new PasswordResetRequests.Accepted(ACCEPTED_MESSAGE);
    }

    /** 新しいパスワードを設定する。鍵が使えなければ 400（InvalidAccountLinkException） */
    @Transactional
    public void confirm(String token, String newPassword) {
        AccountTokens.Verified verified = accountTokens.verify(token, JwtPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new InvalidAccountLinkException(INVALID_LINK_MESSAGE));
        // 同じ鍵で同時に送られても、1回だけ通す（ユーザーの行をロックしてから世代を比べる）
        User user = userRepository.findByIdForUpdate(verified.userId())
                .filter(verified::matches)
                .orElseThrow(() -> new InvalidAccountLinkException(INVALID_LINK_MESSAGE));
        user.changePassword(passwordEncoder.encode(newPassword));
        accountMails.passwordChanged(user, LocalDateTime.now(clock));
        // ログインの①（このアドレス × IP アドレス）を消す。②は消さない（LoginFailureLimits）。メールを送った時点（request）では消さない。
        // 保存（コミット）の前に消している。保存に失敗すると、①が消えてパスワードが変わっていない状態になるが、
        // そこに辿り着けるのはメールを受け取った本人だけで、得られるのは入力のやり直し5回分にすぎない。
        // 再設定をもう一度行えば済むため、順番を厳密にするだけの価値がない
        loginFailureLimits.passwordReset(user.getEmail());
    }
}
