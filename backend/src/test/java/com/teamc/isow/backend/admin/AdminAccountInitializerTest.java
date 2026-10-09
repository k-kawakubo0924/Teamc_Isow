package com.teamc.isow.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 起動時に ADMIN_EMAIL のユーザーを管理者にする処理（docs/admin.md「管理者アカウントの用意」）の確認 */
@SpringBootTest
class AdminAccountInitializerTest {

    private static final String TARGET = "admin-init-target@example.com";
    private static final String OTHER = "admin-init-other@example.com";

    @Autowired
    private AdminAccountInitializer initializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        userRepository.save(new User(TARGET, "09000000120", "hash", "admin_init_target"));
        userRepository.save(new User(OTHER, "09000000121", "hash", "admin_init_other"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", TARGET, OTHER);
    }

    @Test
    void 指定したメールアドレスのユーザーだけが管理者になる() {
        initializer.promote(TARGET);

        assertThat(role(TARGET)).isEqualTo("ADMIN");
        assertThat(role(OTHER)).isEqualTo("USER");
    }

    @Test
    void 大文字や前後の空白があっても_新規会員登録と同じくそろえて探す() {
        initializer.promote("  Admin-Init-TARGET@Example.com ");

        assertThat(role(TARGET)).isEqualTo("ADMIN");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "nobody@example.com"})
    void 空や登録されていないメールアドレスなら何もしない(String email) {
        assertThat(initializer.promote(email)).isNull();

        assertThat(role(TARGET)).isEqualTo("USER");
        assertThat(role(OTHER)).isEqualTo("USER");
    }

    @Test
    void すでに管理者なら何もしない() {
        initializer.promote(TARGET);

        assertThat(initializer.promote(TARGET)).isNull();
        assertThat(role(TARGET)).isEqualTo("ADMIN");
    }

    @Test
    void 新規会員登録で作られるユーザーは一般の利用者() {
        assertThat(role(OTHER)).isEqualTo("USER");
    }

    private String role(String email) {
        return jdbcTemplate.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email);
    }
}
