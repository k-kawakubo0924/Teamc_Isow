package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.auth.AuthInputNormalizer;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 起動時に、環境変数 ADMIN_EMAIL に書かれたメールアドレスの登録済みユーザーを管理者にする（docs/admin.md「管理者アカウントの用意」）。
 * チーム全員が、各自の開発環境で自分のアカウントを管理者にして、管理画面を開けるようにするため。
 *
 * <ul>
 *   <li>メールアドレスは新規会員登録と同じく、前後の空白を除き小文字にそろえて探す</li>
 *   <li>空・ユーザーがいない場合は何もしない（起動は止めない）</li>
 *   <li>既存の管理者を USER に戻すことはしない（ADMIN_EMAIL を書き換えても、前に指定したユーザーは管理者のまま）</li>
 *   <li>管理者にしたら、管理操作のログに「操作した人＝システム」として残す（同じトランザクション）。
 *       ログを残せなければ管理者にもしない。起動時はその失敗をアプリのログに出すだけで、起動は続ける</li>
 * </ul>
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final AdminOperationLogger operationLogger;
    private final TransactionTemplate transactionTemplate;
    private final String adminEmail;

    public AdminAccountInitializer(
            UserRepository userRepository,
            AdminOperationLogger operationLogger,
            PlatformTransactionManager transactionManager,
            @Value("${app.admin.email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.operationLogger = operationLogger;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.adminEmail = adminEmail;
    }

    /** 起動時の処理。管理者にする処理が失敗しても、アプリの起動は止めない */
    @Override
    public void run(ApplicationArguments args) {
        promoteSafely(adminEmail);
    }

    /**
     * 管理者にする（起動時の入り口）。失敗（ログを残せないなど）はアプリのログにエラーとして出すだけで、例外は投げない。
     * 管理者にする操作とログは同じトランザクションのため、失敗したら管理者にもならない（次の起動でもう一度試みる）
     */
    public void promoteSafely(String email) {
        try {
            transactionTemplate.executeWithoutResult(status -> doPromote(email));
        } catch (RuntimeException e) {
            log.error("ADMIN_EMAIL のユーザーを管理者にできませんでした（管理操作のログを残せなかったなど）。管理者にはしていません", e);
        }
    }

    /**
     * email のユーザーを管理者にする（テストからも呼ぶ）。管理者にしたユーザーを返し、しなかった場合は null。
     * ログを残せなければ例外を投げ、管理者にする操作も取り消される
     */
    @Transactional
    public User promote(String email) {
        return doPromote(email);
    }

    /** トランザクションの中で呼ぶ */
    private User doPromote(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = AuthInputNormalizer.email(email);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null) {
            log.warn("ADMIN_EMAIL のユーザーが見つからないため、管理者にしませんでした（先に新規会員登録が必要）: {}", normalized);
            return null;
        }
        if (user.isAdmin()) {
            log.info("ADMIN_EMAIL のユーザーは、すでに管理者です: id={}, username={}", user.getId(), user.getUsername());
            return null;
        }
        user.promoteToAdmin();
        // 操作した人はシステム。detail にメールアドレスなど個人を特定する情報は書かない（ユーザー名だけ）
        operationLogger.recordBySystem(AdminAction.USER_PROMOTED_TO_ADMIN, AdminTargetType.USER, user.getId(),
                "ADMIN_EMAIL による起動時の昇格（username=" + user.getUsername() + "）");
        log.info("ADMIN_EMAIL のユーザーを管理者にしました: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }
}
