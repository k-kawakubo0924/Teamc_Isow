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
import org.springframework.transaction.annotation.Transactional;

/**
 * 起動時に、環境変数 ADMIN_EMAIL に書かれたメールアドレスの登録済みユーザーを管理者にする（docs/admin.md「管理者アカウントの用意」）。
 * チーム全員が、各自の開発環境で自分のアカウントを管理者にして、管理画面を開けるようにするため。
 *
 * <ul>
 *   <li>メールアドレスは新規会員登録と同じく、前後の空白を除き小文字にそろえて探す</li>
 *   <li>空・ユーザーがいない場合は何もしない（起動は止めない）</li>
 *   <li>既存の管理者を USER に戻すことはしない（ADMIN_EMAIL を書き換えても、前に指定したユーザーは管理者のまま）</li>
 * </ul>
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final String adminEmail;

    public AdminAccountInitializer(UserRepository userRepository, @Value("${app.admin.email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        promote(adminEmail);
    }

    /** email のユーザーを管理者にする（テストからも呼ぶ）。管理者にしたユーザーを返し、しなかった場合は null */
    @Transactional
    public User promote(String email) {
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
        log.info("ADMIN_EMAIL のユーザーを管理者にしました: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }
}
