package com.teamc.isow.backend.account;

import com.teamc.isow.backend.auth.AccountMails;
import com.teamc.isow.backend.auth.AccountTokens;
import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.auth.InvalidAccountLinkException;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.config.JwtPurpose;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * メールアドレスの変更（docs/settings.md「メールアドレスの変更」）。
 *
 * <ul>
 *   <li>現在のパスワードを求める（アカウントの所有権を移す操作のため。開いたままの端末を触られただけで奪われないように）。
 *       失敗の回数はパスワードの変更と合わせて数える（CurrentPasswordVerifier）</li>
 *   <li>新しいアドレスに確認のリンク（purpose=email_change の JWT。24時間）を送り、開いた時点で変更を完了する。
 *       完了するまでは古いアドレスでログインできる。鍵には申し込みの ID だけを入れ、アドレスは入れない</li>
 *   <li>新しいアドレスがすでに使われていても、申し込んだ人には同じ応答を返し、そのアドレスの持ち主にだけ知らせる。
 *       ただし新規会員登録は「既に登録されています」と返すため、登録の画面からは分かる（docs/auth.md「登録の画面との食い違い」）</li>
 *   <li>完了したら、古いアドレスに「変更されました」と知らせ、トークンの世代を進めてすべての端末をログアウトさせる</li>
 *   <li>申し込みは1時間に3回まで（超えたら 429。「あと約N分」と Retry-After で、もう一度申し込めるまでの時間を伝える）。
 *       アプリのログにアドレス・鍵を書かない</li>
 * </ul>
 */
@Service
public class EmailChangeService {

    static final Duration CONFIRM_VALIDITY = Duration.ofHours(24);
    static final int MAX_REQUESTS_PER_HOUR = 3;

    /** 回数を数える長さ（1時間） */
    static final Duration LIMIT_WINDOW = Duration.ofHours(1);

    static final String ACCEPTED_MESSAGE =
            "確認メールを送りました。新しいメールアドレスに届いたリンクを開くと、変更が完了します。完了するまでは、今のメールアドレスでログインできます。";

    static final String INVALID_LINK_MESSAGE =
            "このリンクは使えません。有効期限（24時間）が切れたか、すでに使われているか、新しい手続きで無効になりました。もう一度、変更の手続きをしてください。";

    /** 鍵に入れる、申し込みの ID のクレーム */
    private static final String REQUEST_CLAIM = "req";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final EmailChangeRequestRepository requestRepository;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final AccountTokens accountTokens;
    private final AccountMails accountMails;
    private final Clock clock;

    public EmailChangeService(AuthService authService, UserRepository userRepository,
            EmailChangeRequestRepository requestRepository, CurrentPasswordVerifier currentPasswordVerifier,
            AccountTokens accountTokens, AccountMails accountMails, Clock clock) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.currentPasswordVerifier = currentPasswordVerifier;
        this.accountTokens = accountTokens;
        this.accountMails = accountMails;
        this.clock = clock;
    }

    /** 変更を申し込む。入力の誤りは 400、回数の上限は 429。新しいアドレスが使われていても同じ応答 */
    @Transactional
    public AccountRequests.Accepted request(String subject, AccountRequests.EmailChange request) {
        // 回数の制限を同時に押されても超えないよう、ユーザーの行をロックしてから数える
        User user = userRepository.findByIdForUpdate(authService.requireCurrentUser(subject).getId()).orElseThrow();
        currentPasswordVerifier.verify(user, request.currentPassword());
        if (!request.newEmail().equals(request.confirmEmail())) {
            throw InputValidationException.of("confirmEmail", "確認用メールアドレスが一致しません");
        }
        if (request.newEmail().equals(user.getEmail())) {
            throw InputValidationException.of("newEmail", "今のメールアドレスと同じです");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime windowStart = now.minus(LIMIT_WINDOW);
        if (requestRepository.countByUserIdAndCreatedAtAfter(user.getId(), windowStart) >= MAX_REQUESTS_PER_HOUR) {
            // 直近1時間で最も古い申し込みが1時間を過ぎれば、もう一度申し込める。その残り時間を文言と Retry-After に入れる
            Duration retryAfter = requestRepository
                    .findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(user.getId(), windowStart)
                    .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(LIMIT_WINDOW)))
                    .orElse(LIMIT_WINDOW);
            throw new EmailChangeLimitExceededException(retryAfter);
        }
        // 前の変更待ちは無効にする（変更待ちは1人1件だけ）
        requestRepository.findPending(user.getId()).forEach(pending -> pending.supersede(now));

        Optional<User> owner = userRepository.findByEmail(request.newEmail());
        if (owner.isPresent()) {
            // すでに使われている。申し込んだ人には同じ応答を返し、そのアドレスの持ち主にだけ知らせる
            requestRepository.save(EmailChangeRequest.rejected(user, now));
            accountMails.emailAlreadyRegistered(owner.get());
        } else {
            EmailChangeRequest pending = requestRepository.save(EmailChangeRequest.pending(user, request.newEmail(), now));
            String token = accountTokens.issue(JwtPurpose.EMAIL_CHANGE, user, CONFIRM_VALIDITY,
                    Map.of(REQUEST_CLAIM, pending.getId()));
            accountMails.emailChangeConfirm(user, request.newEmail(), token);
        }
        return new AccountRequests.Accepted(ACCEPTED_MESSAGE);
    }

    /** 確認のリンクの鍵で変更を完了する。ログインしていなくてよい（鍵が、新しいアドレスを受け取れる本人であることの証明になる） */
    @Transactional
    public void confirm(String token) {
        AccountTokens.Verified verified = accountTokens.verify(token, JwtPurpose.EMAIL_CHANGE)
                .orElseThrow(EmailChangeService::invalidLink);
        User user = userRepository.findByIdForUpdate(verified.userId())
                .filter(verified::matches)
                .orElseThrow(EmailChangeService::invalidLink);
        EmailChangeRequest request = requestRepository.findById(requestId(verified))
                .filter(r -> r.getUser().getId().equals(user.getId()) && r.isPending())
                .orElseThrow(EmailChangeService::invalidLink);
        LocalDateTime now = LocalDateTime.now(clock);
        String newEmail = request.getNewEmail();
        String oldEmail = user.getEmail();
        if (userRepository.findByEmail(newEmail).isPresent()) {
            // 申し込みの後に、ほかで登録された（変更しない。申し込みは変更待ちのまま残るが、アドレスが使われている間は完了できない）
            throw invalidLink();
        }
        user.changeEmail(newEmail);
        request.complete(now);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // ほぼ同時に同じアドレスが登録された（一意制約）
            throw invalidLink();
        }
        accountMails.emailChanged(user, oldEmail, now);
    }

    private static Long requestId(AccountTokens.Verified verified) {
        Object value = verified.jwt().getClaims().get(REQUEST_CLAIM);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw invalidLink();
    }

    private static InvalidAccountLinkException invalidLink() {
        return new InvalidAccountLinkException(INVALID_LINK_MESSAGE);
    }
}
