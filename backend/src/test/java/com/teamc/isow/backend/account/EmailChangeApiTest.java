package com.teamc.isow.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.mail.OutgoingMail;
import com.teamc.isow.backend.mail.RecordingMailSender;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * メールアドレスの変更（docs/settings.md「メールアドレスの変更」）。
 *
 * <ul>
 *   <li>現在のパスワードを求める（設計画像にはない欄。開いたままの端末を触られただけで奪われないように）</li>
 *   <li>新しいアドレスに確認メールを送り、リンクを開いた時点で変更が完了する。完了するまでは古いアドレスでログインできる</li>
 *   <li>新しいアドレスがすでに使われていても、申し込んだ人には同じ応答を返す（そのアドレスの持ち主にだけ知らせる）</li>
 *   <li>完了したら、古いアドレスに「変更されました」と知らせ、トークンの世代を進めてすべての端末をログアウトさせる</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class EmailChangeApiTest {

    private static final String OLD = "email-change-old@example.com";
    private static final String NEW = "email-change-new@example.com";
    private static final String TAKEN = "email-change-taken@example.com";
    private static final String PASSWORD = "password123";
    private static final String ACCEPTED =
            "確認メールを送りました。新しいメールアドレスに届いたリンクを開くと、変更が完了します。完了するまでは、今のメールアドレスでログインできます。";
    private static final String INVALID_LINK =
            "このリンクは使えません。有効期限（24時間）が切れたか、すでに使われているか、新しい手続きで無効になりました。もう一度、変更の手続きをしてください。";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecordingMailSender mailSender;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        mailSender.clear();
        user = userRepository.save(new User(OLD, "09000000951", passwordEncoder.encode(PASSWORD), "email_change_user"));
        userRepository.save(new User(TAKEN, "09000000952", passwordEncoder.encode(PASSWORD), "email_change_other"));
        bearer = "Bearer " + loginToken(OLD);
    }

    @AfterEach
    void tearDown() {
        mailSender.clear();
        jdbcTemplate.update("DELETE FROM email_change_requests");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "email-change-%@example.com");
    }

    // ---- 申し込み ----

    @Test
    void 新しいアドレスに確認のリンクを送り_完了するまでは古いアドレスでログインできる() throws Exception {
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(ACCEPTED));

        OutgoingMail mail = onlyMailTo(NEW);
        assertThat(mail.subject()).isEqualTo("【ISHO】メールアドレスの確認");
        assertThat(RecordingMailSender.firstLink(mail)).startsWith("http://localhost:5173/email-change/confirm#token=");
        assertThat(mail.body()).contains("@email_change_user", "24時間", "心当たりがない場合");
        assertThat(mail.body()).doesNotContain(OLD);
        login(OLD).andExpect(status().isOk());
        login(NEW).andExpect(status().isUnauthorized());
        assertThat(mailSender.sentTo(OLD)).isEmpty();
    }

    @Test
    void 現在のパスワードが違えば400で何も送らない() throws Exception {
        change("wrongpass1", NEW, NEW).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("現在のパスワードが正しくありません"));
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    void 確認用が違う_形式の誤り_今と同じアドレスは400() throws Exception {
        change(PASSWORD, NEW, "email-change-other@example.com").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.confirmEmail").value("確認用メールアドレスが一致しません"));
        change(PASSWORD, "not-an-address", "not-an-address").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newEmail").exists());
        change(PASSWORD, " Email-Change-Old@Example.com", "email-change-old@example.com").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newEmail").value("今のメールアドレスと同じです"));
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    void すでに使われているアドレスでも同じ応答を返し_そのアドレスの持ち主にだけ知らせる() throws Exception {
        change(PASSWORD, TAKEN, TAKEN).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(ACCEPTED));

        OutgoingMail mail = onlyMailTo(TAKEN);
        assertThat(mail.subject()).isEqualTo("【ISHO】メールアドレスの変更の手続きについて");
        assertThat(mail.body()).doesNotContain("http", "@email_change_user", OLD);
        assertThat(mailSender.sentTo(OLD)).isEmpty();
    }

    @Test
    void 申し込めるのは1時間に3回まで_超えると429() throws Exception {
        for (int i = 0; i < 3; i++) {
            change(PASSWORD, "email-change-n" + i + "@example.com", "email-change-n" + i + "@example.com")
                    .andExpect(status().isAccepted());
        }
        // 最も古い申し込みを40分前にする。その1時間後（今から約20分後）に、もう一度申し込めるようになる
        jdbcTemplate.update("UPDATE email_change_requests SET created_at = ? WHERE id = (SELECT MIN(id) FROM email_change_requests)",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(40)));
        String retryAfter = change(PASSWORD, NEW, NEW).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        "メールアドレスの変更の手続きは1時間に3回までです。あと約20分で、もう一度申し込めます。"))
                .andReturn().getResponse().getHeader("Retry-After");
        // Retry-After は秒（約20分。テストの実行にかかった分だけ少ない）
        assertThat(retryAfter).isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(20 * 60 - 60, 20 * 60);

        jdbcTemplate.update("UPDATE email_change_requests SET created_at = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(61)));
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
    }

    @Test
    void 残りが1分に満たなくても_あと約1分と出す() throws Exception {
        for (int i = 0; i < 3; i++) {
            change(PASSWORD, "email-change-n" + i + "@example.com", "email-change-n" + i + "@example.com")
                    .andExpect(status().isAccepted());
        }
        jdbcTemplate.update("UPDATE email_change_requests SET created_at = ? WHERE id = (SELECT MIN(id) FROM email_change_requests)",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(59).minusSeconds(50)));

        String retryAfter = change(PASSWORD, NEW, NEW).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        "メールアドレスの変更の手続きは1時間に3回までです。あと約1分で、もう一度申し込めます。"))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 10);
    }

    @Test
    void トークンなしは401() throws Exception {
        mockMvc.perform(post("/api/users/me/email-change").contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW, NEW)))
                .andExpect(status().isUnauthorized());
    }

    // ---- 完了 ----

    @Test
    void リンクを開いて完了すると新しいアドレスでログインでき_古いアドレスに知らせ_すべての端末がログアウトになる() throws Exception {
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(NEW));

        confirm(token).andExpect(status().isNoContent());

        login(NEW).andExpect(status().isOk());
        login(OLD).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        OutgoingMail notice = onlyMailTo(OLD);
        assertThat(notice.subject()).isEqualTo("【ISHO】メールアドレスが変更されました");
        assertThat(notice.body()).contains("@email_change_user", "このメールアドレスではログインできません", "取り戻す手段");
        // 新しいアドレスは書かない。手段がないのに「運営にご連絡ください」とは案内しない
        assertThat(notice.body()).doesNotContain(NEW, "ご連絡ください", "お問い合わせ");
    }

    @Test
    void リンクは1回だけ_新しく申し込むと前のリンクは使えない() throws Exception {
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        String first = RecordingMailSender.tokenOf(onlyMailTo(NEW));
        change(PASSWORD, "email-change-newer@example.com", "email-change-newer@example.com").andExpect(status().isAccepted());
        String second = RecordingMailSender.tokenOf(onlyMailTo("email-change-newer@example.com"));

        confirm(first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID_LINK));
        confirm(second).andExpect(status().isNoContent());
        confirm(second).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID_LINK));
        login("email-change-newer@example.com").andExpect(status().isOk());
    }

    @Test
    void 申し込みの後にパスワードを変えたらリンクは使えない() throws Exception {
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(NEW));
        jdbcTemplate.update("UPDATE users SET token_version = token_version + 1 WHERE id = ?", user.getId());

        confirm(token).andExpect(status().isBadRequest());
        login(OLD).andExpect(status().isOk());
    }

    /*
     * 乗っ取りの途中で本人が対処したときに、乗っ取りが完了しないこと（docs/settings.md「パスワードを変えると、申し込み中の変更は完了できない」）。
     * 攻撃者がアカウントに入って自分のアドレスへの変更を申し込み、本人が気づいてパスワードを再設定・変更しても、
     * 攻撃者が自分のメールボックスの確認リンクを押せば変更が完了してしまうと、本人が正しく対処したのに乗っ取りが完了する。
     * 確認の完了にはログインが要らない（ログアウトさせても止められない）ため、リンクの鍵に入れたトークンの世代で止めている。
     * 世代は再設定・変更で進むので、その前に発行した確認のリンクは使えなくなる
     */

    @Test
    void 攻撃者が変更を申し込んだ後に本人がパスワードを再設定したら_攻撃者の確認リンクでは変更が完了しない() throws Exception {
        // 1. 攻撃者がアカウントに入り、自分のアドレスへの変更を申し込む
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        String attackerLink = RecordingMailSender.tokenOf(onlyMailTo(NEW));
        // 2. 本人が気づいて、ログイン前の再設定（メールのリンク）でパスワードを変える
        mockMvc.perform(post("/api/auth/password-reset/request").with(req -> {
                    req.setRemoteAddr("10.0.3.1");
                    return req;
                }).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("email", OLD))))
                .andExpect(status().isAccepted());
        String resetToken = RecordingMailSender.tokenOf(onlyMailTo(OLD));
        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("token", resetToken, "newPassword", "newpass456"))))
                .andExpect(status().isNoContent());

        // 3. 攻撃者が自分のメールボックスの確認リンクを押しても、完了しない
        confirm(attackerLink).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID_LINK));

        // 4. アドレスは本人のまま。本人は新しいパスワードで入れる。古いアドレスに「変更されました」は届かない
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user.getId()))
                .isEqualTo(OLD);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", OLD, "password", "newpass456"))))
                .andExpect(status().isOk());
        assertThat(mailSender.sentTo(OLD)).extracting(OutgoingMail::subject)
                .doesNotContain("【ISHO】メールアドレスが変更されました");
    }

    @Test
    void 攻撃者が変更を申し込んだ後に本人がログイン中にパスワードを変えたら_攻撃者の確認リンクでは変更が完了しない() throws Exception {
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        String attackerLink = RecordingMailSender.tokenOf(onlyMailTo(NEW));
        // 本人が、まだログインしている端末からパスワードを変える
        mockMvc.perform(post("/api/users/me/password").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "newpass456"))))
                .andExpect(status().isOk());

        confirm(attackerLink).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID_LINK));

        assertThat(jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user.getId()))
                .isEqualTo(OLD);
    }

    @Test
    void 完了するまでの間に新しいアドレスがほかで登録されたら変更しない() throws Exception {
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(NEW));
        userRepository.save(new User(NEW, "09000000953", passwordEncoder.encode(PASSWORD), "email_change_late"));

        confirm(token).andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user.getId())).isEqualTo(OLD);
    }

    @Test
    void 確認の鍵は普通のAPIでは使えず_ログインしていない端末でも完了できる() throws Exception {
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(NEW));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        confirm(token).andExpect(status().isNoContent());
    }

    @Test
    void 変更が終わったら申し込みの行に新しいアドレスを残さない() throws Exception {
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        change(PASSWORD, "email-change-newer@example.com", "email-change-newer@example.com").andExpect(status().isAccepted());
        confirm(RecordingMailSender.tokenOf(onlyMailTo("email-change-newer@example.com"))).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForList("SELECT new_email FROM email_change_requests", String.class))
                .containsOnlyNulls();
    }

    @Test
    void メールアドレス_鍵_パスワードはアプリのログに書かない(CapturedOutput output) throws Exception {
        // 使われているアドレスへの申し込みを先にする（後にすると、変更待ちの申し込みが無効になるため）
        change(PASSWORD, TAKEN, TAKEN).andExpect(status().isAccepted());
        change(PASSWORD, NEW, NEW).andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(NEW));
        confirm(token).andExpect(status().isNoContent());

        assertThat(output.getAll()).doesNotContain(OLD, NEW, TAKEN, token, PASSWORD);
    }

    // ---- 部品 ----

    private ResultActions change(String currentPassword, String newEmail, String confirmEmail) throws Exception {
        return mockMvc.perform(post("/api/users/me/email-change").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body(currentPassword, newEmail, confirmEmail)));
    }

    private static String body(String currentPassword, String newEmail, String confirmEmail) {
        return JSON.writeValueAsString(Map.of("currentPassword", currentPassword, "newEmail", newEmail,
                "confirmEmail", confirmEmail));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", token))));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", PASSWORD))));
    }

    private String loginToken(String email) throws Exception {
        return JsonPath.read(login(email).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private OutgoingMail onlyMailTo(String to) {
        List<OutgoingMail> mails = mailSender.sentTo(to);
        assertThat(mails).hasSize(1);
        return mails.get(0);
    }
}
