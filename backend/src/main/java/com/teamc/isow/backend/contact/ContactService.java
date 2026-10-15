package com.teamc.isow.backend.contact;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.moderation.SuspensionAppealTokens;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * お問い合わせの受付（docs/settings.md「お問い合わせ」）。DB に保存し、管理画面で見る（メールは送らない）。
 *
 * <ul>
 *   <li>ログイン中は POST /api/contacts。利用停止中は、利用停止の 403 で受け取った申し立て用の鍵で POST /api/contacts/appeal
 *       （ログインのトークンは出ないため）。鍵は、その時の利用停止が続いている間だけ使える</li>
 *   <li>返信はしない（受け取るだけ）。画面で「現在、個別の返信は行っていません」と伝える</li>
 *   <li>1人1日（24時間）{@value #MAX_PER_DAY} 件まで。普通のお問い合わせと申し立てを合わせて数える。
 *       IP アドレスは使わない（DB・アプリのログに残さない方針。docs/settings.md「いたずらを防ぐ方法」）</li>
 *   <li><b>内容とメールアドレスは、アプリのログに書かない</b>（個人情報が書かれる前提のため）。例外の message にも入れない</li>
 * </ul>
 */
@Service
public class ContactService {

    /** 1人が1日（24時間）に送れる数 */
    static final int MAX_PER_DAY = 5;

    private final ContactInquiryRepository repository;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final SuspensionAppealTokens appealTokens;
    private final Clock clock;

    public ContactService(ContactInquiryRepository repository, AuthService authService, UserRepository userRepository,
            SuspensionAppealTokens appealTokens, Clock clock) {
        this.repository = repository;
        this.authService = authService;
        this.userRepository = userRepository;
        this.appealTokens = appealTokens;
        this.clock = clock;
    }

    /** ログイン中のお問い合わせ */
    @Transactional
    public ContactResponse send(String subject, ContactRequest request) {
        User user = authService.requireCurrentUser(subject);
        return save(user, ContactChannel.NORMAL, request.category(), request.email(), request.body());
    }

    /**
     * 利用停止中の申し立て。鍵が正しくなければ 401（AppealTokenInvalidException）。
     * 鍵を受け取った後に利用停止が解除された・期限を過ぎた・別の利用停止になったら 409（SuspensionChangedException）
     */
    @Transactional
    public ContactResponse appeal(AppealRequest request) {
        SuspensionAppealTokens.Claims claims = appealTokens.verify(request.appealToken())
                .orElseThrow(AppealTokenInvalidException::new);
        User user = userRepository.findById(claims.userId()).orElseThrow(AppealTokenInvalidException::new);
        if (!user.isSuspendedAt(LocalDateTime.now(clock))
                || !SuspensionAppealTokens.matchesCurrentSuspension(claims, user)) {
            throw new SuspensionChangedException();
        }
        return save(user, ContactChannel.SUSPENDED_APPEAL, request.category(), request.email(), request.body());
    }

    private ContactResponse save(User user, ContactChannel channel, String categoryValue, String email, String body) {
        ContactCategory category = parseCategory(categoryValue);
        LocalDateTime now = LocalDateTime.now(clock);
        if (repository.countByUserIdAndCreatedAtAfter(user.getId(), now.minusDays(1)) >= MAX_PER_DAY) {
            throw new ContactLimitExceededException();
        }
        // 登録済みのアドレスと同じなら預からない（すでに持っているため。別の連絡先のときだけ保存する）
        String otherEmail = email != null && email.equals(user.getEmail()) ? null : email;
        repository.save(new ContactInquiry(user.getId(), channel, category, otherEmail, body, now));
        return ContactResponse.accepted();
    }

    private static ContactCategory parseCategory(String value) {
        try {
            return ContactCategory.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw InputValidationException.of("category", ContactRequest.CATEGORY_MESSAGE);
        }
    }
}
