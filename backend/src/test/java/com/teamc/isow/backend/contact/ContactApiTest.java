package com.teamc.isow.backend.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 利用者のお問い合わせ（POST /api/contacts）と、利用停止中の申し立て（POST /api/contacts/appeal）の確認
 * （docs/settings.md「お問い合わせ」）。
 * 申し立て用の鍵は、利用停止の 403 の応答で渡す。普通の API では使えず、利用停止が解除されたら申し立てにも使えない
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ContactApiTest {

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

    // ---- ログイン中のお問い合わせ ----

    @Test
    void ログイン中は送れて_送った人_種類_内容が通常のお問い合わせとして未対応で残る() throws Exception {
        f.send(f.sender, "", "APP", "使い方を教えてください")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM contact_inquiries WHERE id = ?", f.latestId());
        assertThat(row.get("user_id")).isEqualTo(f.sender.getId());
        assertThat(row.get("channel")).isEqualTo("NORMAL");
        assertThat(row.get("category")).isEqualTo("APP");
        assertThat(row.get("body")).isEqualTo("使い方を教えてください");
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("email")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void 返信先メールアドレスは任意で_登録済みと同じなら保存せず_別のアドレスだけを保存する() throws Exception {
        // 画面は登録済みのアドレスを最初から入れて送る。大文字・前後の空白の違いは同じアドレスとみなす
        f.send(f.sender, "  Contact-Sender@Example.com ", "ACCOUNT", "登録済みのアドレスのまま").andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM contact_inquiries WHERE id = ?", String.class,
                f.latestId())).isNull();

        f.send(f.sender, "other-address@example.com", "ACCOUNT", "別の連絡先").andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM contact_inquiries WHERE id = ?", String.class,
                f.latestId())).isEqualTo("other-address@example.com");
    }

    @Test
    void 種類と内容は必須で_種類は4つのどれか_メールアドレスは形式を確かめる() throws Exception {
        f.send(f.sender, "", "", "  \n ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").value("お問い合わせの種類を選択してください"))
                .andExpect(jsonPath("$.errors.body").value("お問い合わせ内容を入力してください"));
        f.send(f.sender, "", "REFUND", "存在しない種類")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").value("お問い合わせの種類を選択してください"));
        f.send(f.sender, "not-an-address", "OTHER", "形式の誤り")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("メールアドレスの形式が正しくありません"));
        for (String category : new String[] {"APP", "USER", "ACCOUNT", "OTHER"}) {
            f.send(f.sender, "", category, "種類 " + category).andExpect(status().isCreated());
        }
        assertThat(f.count("SELECT count(*) FROM contact_inquiries")).isEqualTo(4);
    }

    @Test
    void 内容は1000文字まで_改行のCRLFは1文字と数え_前後の空白は除く() throws Exception {
        String limit = "あ".repeat(998) + "\r\nい";
        f.send(f.sender, "", "OTHER", "  " + limit + "  ").andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT body FROM contact_inquiries WHERE id = ?", String.class,
                f.latestId())).isEqualTo("あ".repeat(998) + "\nい");

        f.send(f.sender, "", "OTHER", "あ".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").value("お問い合わせ内容は1000文字以内で入力してください"));
    }

    @Test
    void 送れるのは1人1日5件まで_超えると429() throws Exception {
        for (int i = 0; i < 5; i++) {
            f.send(f.sender, "", "OTHER", "お問い合わせ " + i).andExpect(status().isCreated());
        }
        f.send(f.sender, "", "OTHER", "6件目").andExpect(status().isTooManyRequests());
        // ほかの人は数えない
        f.send(f.other, "", "OTHER", "別の人").andExpect(status().isCreated());
        // 24時間より前のものは数えない
        jdbcTemplate.update("UPDATE contact_inquiries SET created_at = DATEADD('HOUR', -25, created_at) WHERE user_id = ?",
                f.sender.getId());
        f.send(f.sender, "", "OTHER", "次の日").andExpect(status().isCreated());
    }

    @Test
    void トークンなしは401_利用停止中のトークンでは普通のお問い合わせは403() throws Exception {
        mockMvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content(ContactFixture.json(Map.of("email", "", "category", "APP", "body", "x"))))
                .andExpect(status().isUnauthorized());
        f.suspend(f.sender);
        f.send(f.sender, "", "APP", "x")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("ACCOUNT_SUSPENDED"));
        assertThat(f.count("SELECT count(*) FROM contact_inquiries")).isZero();
    }

    @Test
    void 内容とメールアドレスはアプリのログに出さない(CapturedOutput output) throws Exception {
        f.send(f.sender, "secret-address@example.com", "USER", "ログに出してはいけない本文").andExpect(status().isCreated());
        f.send(f.sender, "secret-address@example.com", "NOPE", "誤りでもログに出してはいけない本文")
                .andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain("ログに出してはいけない本文", "secret-address@example.com");
    }

    // ---- 利用停止中の申し立て ----

    @Test
    void 利用停止中にログインすると_403の応答に申し立て用の鍵があり_それで申し立てられる() throws Exception {
        f.suspend(f.sender);
        String appealToken = f.appealTokenFromLogin(f.sender);
        assertThat(appealToken).isNotBlank();

        f.appeal(appealToken, "", "ACCOUNT", "利用停止の見直しをお願いします")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM contact_inquiries WHERE id = ?", f.latestId());
        assertThat(row.get("user_id")).isEqualTo(f.sender.getId());
        assertThat(row.get("channel")).isEqualTo("SUSPENDED_APPEAL");
        assertThat(row.get("category")).isEqualTo("ACCOUNT");
        assertThat(row.get("body")).isEqualTo("利用停止の見直しをお願いします");
    }

    @Test
    void ログイン中に利用停止になったときの403にも鍵があり_それで申し立てられる() throws Exception {
        f.suspend(f.sender);
        String body = f.getAs(f.sender, "/api/users/me")
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        String appealToken = JsonPath.read(body, "$.appealToken");

        f.appeal(appealToken, "other-address@example.com", "ACCOUNT", "見直しをお願いします").andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM contact_inquiries WHERE id = ?", String.class,
                f.latestId())).isEqualTo("other-address@example.com");
    }

    @Test
    void パスワードが違うログインの401には鍵を出さない() throws Exception {
        f.suspend(f.sender);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(ContactFixture.json(Map.of("email", f.sender.getEmail(), "password", "wrong-pass1"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("appealToken");
    }

    @Test
    void 申し立て用の鍵で普通のAPIを呼ぶと拒否される() throws Exception {
        f.suspend(f.sender);
        String appealToken = f.appealTokenFromLogin(f.sender);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + appealToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/contacts").header("Authorization", "Bearer " + appealToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ContactFixture.json(Map.of("email", "", "category", "APP", "body", "x"))))
                .andExpect(status().isUnauthorized());
        // 管理者の鍵でも同じ（purpose 付きのトークンは、誰のものでも普通の API では使えない）
        String adminPurposeToken = f.purposeToken(f.admin, "suspension_appeal", Instant.now().plus(Duration.ofMinutes(30)));
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + adminPurposeToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 利用停止を解除した後_その鍵は使えない() throws Exception {
        f.suspend(f.sender);
        String appealToken = f.appealTokenFromLogin(f.sender);
        f.postAs(f.admin, "/api/admin/moderation/users/" + f.sender.getId() + "/unsuspend", "{\"memo\": \"\"}")
                .andExpect(status().isOk());

        // 申し立てには使えない（利用停止ではなくなったため。もう一度ログインすれば使える）
        f.appeal(appealToken, "", "ACCOUNT", "解除の後の申し立て")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("利用停止の状態が変わったため、送信できませんでした。もう一度ログインしてください。"));
        // 普通の API でも使えない（解除されたからといって、ログインのトークンの代わりにはならない）
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + appealToken))
                .andExpect(status().isUnauthorized());
        assertThat(f.count("SELECT count(*) FROM contact_inquiries")).isZero();
    }

    @Test
    void 解除した後にもう一度利用停止になっても_前の利用停止の鍵は使えない() throws Exception {
        f.suspend(f.sender);
        String oldToken = f.appealTokenFromLogin(f.sender);
        Timestamp firstSuspendedAt = jdbcTemplate.queryForObject("SELECT suspended_at FROM users WHERE id = ?",
                Timestamp.class, f.sender.getId());
        f.postAs(f.admin, "/api/admin/moderation/users/" + f.sender.getId() + "/unsuspend", "{\"memo\": \"\"}")
                .andExpect(status().isOk());
        // 2回目の利用停止（1回目の1時間後。どちらも今より前）
        jdbcTemplate.update("UPDATE users SET suspended_at = ?, suspended_until = NULL, suspension_reason = 'SPAM' "
                + "WHERE id = ?", Timestamp.valueOf(firstSuspendedAt.toLocalDateTime().plusHours(1)), f.sender.getId());

        f.appeal(oldToken, "", "ACCOUNT", "前の鍵").andExpect(status().isConflict());
        f.appeal(f.appealTokenFromLogin(f.sender), "", "ACCOUNT", "新しい鍵").andExpect(status().isCreated());
    }

    @Test
    void 期限切れ_普通のログイン用トークン_別のpurpose_壊れた鍵では申し立てられず401() throws Exception {
        f.suspend(f.sender);
        // 検証は60秒のずれを許すため、それより前に切れたものにする
        String expired = f.purposeToken(f.sender, "suspension_appeal", Instant.now().minus(Duration.ofMinutes(10)));
        String otherPurpose = f.purposeToken(f.sender, "password_reset", Instant.now().plus(Duration.ofMinutes(30)));
        String loginToken = f.bearer(f.sender).substring("Bearer ".length());
        String valid = f.appealTokenFromLogin(f.sender);
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("A") ? "BB" : "AA");

        for (String token : new String[] {expired, otherPurpose, loginToken, tampered, "not-a-token"}) {
            f.appeal(token, "", "ACCOUNT", "通らない申し立て")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("受付の期限が切れました。もう一度ログインしてから送ってください。"));
        }
        f.appeal("", "", "ACCOUNT", "鍵なし")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.appealToken").exists());
        assertThat(f.count("SELECT count(*) FROM contact_inquiries")).isZero();
    }

    @Test
    void 申し立ても入力の誤りは400で_1日の上限は普通のお問い合わせと合わせて数える() throws Exception {
        for (int i = 0; i < 4; i++) {
            f.send(f.sender, "", "OTHER", "利用停止の前 " + i).andExpect(status().isCreated());
        }
        f.suspend(f.sender);
        String appealToken = f.appealTokenFromLogin(f.sender);

        f.appeal(appealToken, "", "", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").exists())
                .andExpect(jsonPath("$.errors.body").exists());
        f.appeal(appealToken, "", "ACCOUNT", "5件目").andExpect(status().isCreated());
        f.appeal(appealToken, "", "ACCOUNT", "6件目").andExpect(status().isTooManyRequests());
    }
}
