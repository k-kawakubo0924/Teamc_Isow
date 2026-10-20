package com.teamc.isow.backend.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.admin.AdminOperationLogRepository;
import com.teamc.isow.backend.common.AppLog;
import com.teamc.isow.backend.common.AppLogExtension;
import com.teamc.isow.backend.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 管理者のお問い合わせの確認（/api/admin/contacts/**。docs/admin.md「7. お問い合わせ」）。
 * 一覧には内容・メールアドレスを出さない。詳細を見たことはログに残さない。状態の変更は同じトランザクションでログに残す
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(AppLogExtension.class)
class AdminContactApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private AdminOperationLogRepository logRepository;

    private ContactFixture f;

    @BeforeEach
    void setUp() {
        f = new ContactFixture(mockMvc, jwtEncoder, userRepository, jdbcTemplate, passwordEncoder);
        f.createUsers();
    }

    @AfterEach
    void tearDown() {
        f.cleanUp();
    }

    // ---- 一覧 ----

    @Test
    void 一覧は新しい順で_内容とメールアドレスを出さず_状態_種類_受付の経路で絞れる() throws Exception {
        f.send(f.other, "list-secret@example.com", "APP", "一覧に出さない本文その1").andExpect(status().isCreated());
        long first = f.latestId();
        f.suspend(f.sender);
        f.appeal(f.appealTokenFromLogin(f.sender), "", "ACCOUNT", "一覧に出さない本文その2").andExpect(status().isCreated());
        long appeal = f.latestId();

        String body = f.getAs(f.admin, "/api/admin/contacts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) appeal, (int) first)))
                .andExpect(jsonPath("$.items[0].channel").value("SUSPENDED_APPEAL"))
                .andExpect(jsonPath("$.items[0].category").value("ACCOUNT"))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].userId").value(f.sender.getId()))
                .andExpect(jsonPath("$.items[0].username").value("contact_sender"))
                .andExpect(jsonPath("$.items[1].channel").value("NORMAL"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("一覧に出さない本文", "list-secret@example.com");

        f.getAs(f.admin, "/api/admin/contacts?channel=SUSPENDED_APPEAL")
                .andExpect(jsonPath("$.items[*].id", contains((int) appeal)));
        f.getAs(f.admin, "/api/admin/contacts?category=APP")
                .andExpect(jsonPath("$.items[*].id", contains((int) first)));
        f.getAs(f.admin, "/api/admin/contacts?status=RESOLVED").andExpect(jsonPath("$.items").isEmpty());
        f.getAs(f.admin, "/api/admin/contacts?status=NOPE").andExpect(status().isBadRequest());
    }

    @Test
    void 管理者でなければ404_トークンなしは401() throws Exception {
        f.send(f.sender, "", "APP", "x").andExpect(status().isCreated());
        long id = f.latestId();

        f.getAs(f.sender, "/api/admin/contacts").andExpect(status().isNotFound());
        f.getAs(f.sender, "/api/admin/contacts/" + id).andExpect(status().isNotFound());
        f.postAs(f.sender, "/api/admin/contacts/" + id + "/status", "{\"status\": \"RESOLVED\"}")
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/contacts")).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM contact_inquiries WHERE id = ?", String.class, id))
                .isEqualTo("OPEN");
    }

    // ---- 詳細 ----

    @Test
    void 詳細は内容と別の連絡先を返し_見たことはログに残さない() throws Exception {
        f.send(f.sender, "other-address@example.com", "USER", "詳細で見る本文\n2行目").andExpect(status().isCreated());
        long id = f.latestId();

        f.getAs(f.admin, "/api/admin/contacts/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.userId").value(f.sender.getId()))
                .andExpect(jsonPath("$.username").value("contact_sender"))
                .andExpect(jsonPath("$.channel").value("NORMAL"))
                .andExpect(jsonPath("$.category").value("USER"))
                .andExpect(jsonPath("$.email").value("other-address@example.com"))
                .andExpect(jsonPath("$.body").value("詳細で見る本文\n2行目"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").exists());
        f.getAs(f.admin, "/api/admin/contacts/999999").andExpect(status().isNotFound());
        // お問い合わせは本人が運営に宛てて送ったもので、読むこと自体が目的のため、見たことは記録しない（DM とは違う）
        assertThat(f.count("SELECT count(*) FROM admin_operation_logs")).isZero();
    }

    @Test
    void 送った人が退会しても詳細は見られ_ユーザー名は空になる() throws Exception {
        f.send(f.other, "", "OTHER", "退会する人のお問い合わせ").andExpect(status().isCreated());
        long id = f.latestId();
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", f.other.getId());

        f.getAs(f.admin, "/api/admin/contacts/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(f.other.getId()))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.body").value("退会する人のお問い合わせ"));
    }

    // ---- 状態の記録 ----

    @Test
    void 対応済みにすると状態_終了日時_対応した管理者_メモが残り_ログには状態だけを書く(AppLog appLog) throws Exception {
        f.send(f.sender, "log-secret@example.com", "APP", "ログに書かない本文").andExpect(status().isCreated());
        long id = f.latestId();

        f.postAs(f.admin, "/api/admin/contacts/" + id + "/status",
                        ContactFixture.json(Map.of("status", "RESOLVED", "memo", "ログに書かないメモ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.handledByUsername").value("contact_admin"))
                .andExpect(jsonPath("$.adminMemo").value("ログに書かないメモ"));

        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("action")).isEqualTo("CONTACT_RESOLVED");
        assertThat(log.get("target_type")).isEqualTo("CONTACT");
        assertThat(log.get("target_id")).isEqualTo(id);
        assertThat(log.get("operator_id")).isEqualTo(f.admin.getId());
        assertThat(log.get("detail")).isEqualTo("状態：対応済み");
        assertThat(appLog.text()).doesNotContain("ログに書かない本文", "log-secret@example.com", "ログに書かないメモ");
    }

    @Test
    void 対応不要にし_未対応に戻せて_それぞれログに残り_同じ状態にするのは409() throws Exception {
        f.send(f.sender, "", "OTHER", "いたずら").andExpect(status().isCreated());
        long id = f.latestId();
        String path = "/api/admin/contacts/" + id + "/status";

        f.postAs(f.admin, path, "{\"status\": \"DISMISSED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
        f.postAs(f.admin, path, "{\"status\": \"DISMISSED\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("すでに「対応不要」です。"));
        f.postAs(f.admin, path, "{\"status\": \"OPEN\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closedAt").doesNotExist());

        assertThat(jdbcTemplate.queryForList("SELECT action FROM admin_operation_logs ORDER BY id", String.class))
                .containsExactly("CONTACT_DISMISSED", "CONTACT_REOPENED");
    }

    @Test
    void 状態の値の誤り_長すぎるメモは400_存在しないお問い合わせは404() throws Exception {
        f.send(f.sender, "", "OTHER", "x").andExpect(status().isCreated());
        long id = f.latestId();

        f.postAs(f.admin, "/api/admin/contacts/" + id + "/status", "{\"status\": \"DONE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
        f.postAs(f.admin, "/api/admin/contacts/" + id + "/status",
                        ContactFixture.json(Map.of("status", "RESOLVED", "memo", "あ".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.memo").exists());
        f.postAs(f.admin, "/api/admin/contacts/999999/status", "{\"status\": \"RESOLVED\"}")
                .andExpect(status().isNotFound());
        assertThat(f.count("SELECT count(*) FROM admin_operation_logs")).isZero();
    }

    @Test
    void ログを残せなければ状態の変更も取り消す() throws Exception {
        f.send(f.sender, "", "OTHER", "x").andExpect(status().isCreated());
        long id = f.latestId();
        doThrow(new IllegalStateException("ログの保存に失敗（テスト）")).when(logRepository).save(any());

        assertThatThrownBy(() -> f.postAs(f.admin, "/api/admin/contacts/" + id + "/status", "{\"status\": \"RESOLVED\"}"))
                .hasRootCauseMessage("ログの保存に失敗（テスト）");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM contact_inquiries WHERE id = ?", String.class, id))
                .isEqualTo("OPEN");
    }
}
