package com.teamc.isow.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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

    /** ログの保存をわざと失敗させるために使う（通知のテストと同じやり方） */
    @MockitoSpyBean
    private AdminOperationLogRepository logRepository;

    @BeforeEach
    void setUp() {
        userRepository.save(new User(TARGET, "09000000120", "hash", "admin_init_target"));
        userRepository.save(new User(OTHER, "09000000121", "hash", "admin_init_other"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
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

    // ---- 管理操作のログ（操作した人＝システム） ----

    @Test
    void 管理者にすると_システムの操作としてログに残り_メールアドレスは書かない() {
        User promoted = initializer.promote(TARGET);

        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("operator_id")).isNull();
        assertThat(log.get("operator_username")).isEqualTo("システム");
        assertThat(log.get("action")).isEqualTo("USER_PROMOTED_TO_ADMIN");
        assertThat(log.get("target_type")).isEqualTo("USER");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(promoted.getId());
        assertThat((String) log.get("detail")).contains("admin_init_target").doesNotContain("@");
    }

    @Test
    void すでに管理者なら_何も変えないのでログも残さない() {
        initializer.promote(TARGET);

        initializer.promote(TARGET);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class)).isEqualTo(1);
    }

    @Test
    void ログを残せなければ_管理者にする操作も取り消される() {
        doThrow(new IllegalStateException("ログの保存に失敗（テスト）")).when(logRepository).save(any());

        assertThatThrownBy(() -> initializer.promote(TARGET)).isInstanceOf(IllegalStateException.class);

        assertThat(role(TARGET)).isEqualTo("USER");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class)).isZero();
    }

    @Test
    void 起動時にログを残せなくても_起動の処理は止まらず_管理者にもしない() {
        doThrow(new IllegalStateException("ログの保存に失敗（テスト）")).when(logRepository).save(any());

        // 起動時の処理（run）から呼ばれる入り口。失敗はアプリのログに出すだけで、例外は投げない
        initializer.promoteSafely(TARGET);

        assertThat(role(TARGET)).isEqualTo("USER");
    }

    private String role(String email) {
        return jdbcTemplate.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email);
    }
}
