package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** 管理者向けの API のテストで共通に使う部品 */
final class AdminTestSupport {

    static final String ADMIN_EMAIL = "admin-api-admin@example.com";
    static final String USER_EMAIL = "admin-api-user@example.com";
    /** テストで追加するマスタ・タグの名前の先頭（後片付けで、この名前のものだけを消す） */
    static final String NAME_PREFIX = "管理テスト";

    private AdminTestSupport() {
    }

    /** 管理者のユーザーを作る（role は DB に直接書き込む。画面・API からは管理者にできないため） */
    static User createAdmin(UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        User admin = userRepository.save(new User(ADMIN_EMAIL, "09000000150", "hash", "admin_api_admin"));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        return admin;
    }

    static User createUser(UserRepository userRepository) {
        return userRepository.save(new User(USER_EMAIL, "09000000151", "hash", "admin_api_user"));
    }

    static String bearer(JwtEncoder jwtEncoder, User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /**
     * テストで作ったデータを消し、初期データのマスタ・公式タグを有効に戻す
     * （参照している側から消す。初期データのマスタは他のテストも使うため、消さずに有効に戻す）
     */
    static void cleanUp(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", ADMIN_EMAIL, USER_EMAIL);
        for (String table : new String[] {"fashion_categories", "body_types", "personal_colors", "tags"}) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE name LIKE ?", NAME_PREFIX + "%");
            jdbcTemplate.update("UPDATE " + table + " SET is_active = true");
        }
    }
}
