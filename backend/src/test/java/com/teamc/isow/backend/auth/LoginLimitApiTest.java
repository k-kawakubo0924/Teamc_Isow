package com.teamc.isow.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.common.AppLog;
import com.teamc.isow.backend.common.AppLogExtension;
import com.teamc.isow.backend.mail.OutgoingMail;
import com.teamc.isow.backend.mail.RecordingMailSender;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * ログインの失敗の回数の上限（docs/auth.md「ログインの失敗の回数の上限」）。
 *
 * <ul>
 *   <li>① メールアドレス × IP アドレスごとに、15分に5回まで。成功したら消す</li>
 *   <li>② IP アドレスごとに（アドレスを問わず）、15分に30回まで。<b>成功しても消さない</b></li>
 *   <li>止まっている間は、パスワードを照合せずに 429（正しいパスワードでも通さない）。登録の有無で応答を変えない</li>
 * </ul>
 * 回数はメモリの上で数え、テストの間で残るため、テストごとに別の IP アドレスを使う
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(AppLogExtension.class)
class LoginLimitApiTest {

    private static final String EMAIL = "login-limit-user@example.com";
    private static final String OTHER_EMAIL = "login-limit-other@example.com";
    private static final String PASSWORD = "password123";
    private static final String WRONG = "wrongpass999";
    private static final String NEW_PASSWORD = "newpass456";
    private static final String LIMIT_MESSAGE =
            "ログインの失敗が続いたため、一時的に受け付けを止めています。あと約15分で、もう一度お試しください。"
                    + "パスワードを忘れた場合は「パスワードをお忘れですか？」から再設定できます。";
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

    @BeforeEach
    void setUp() {
        mailSender.clear();
        userRepository.save(new User(EMAIL, "09000000951", passwordEncoder.encode(PASSWORD), "login_limit_user"));
        userRepository.save(new User(OTHER_EMAIL, "09000000952", passwordEncoder.encode(PASSWORD), "login_limit_other"));
    }

    @AfterEach
    void tearDown() {
        mailSender.clear();
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "login-limit-%@example.com");
    }

    // ---- ① メールアドレス × IP アドレス ----

    @Test
    void 五回失敗すると_六回目は正しいパスワードでも429で_あと約N分とRetry_Afterを返す() throws Exception {
        String ip = "10.0.2.1";
        failTimes(EMAIL, ip, 5);

        String retryAfter = login(EMAIL, PASSWORD, ip).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(LIMIT_MESSAGE))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(15 * 60 - 60, 15 * 60);
    }

    @Test
    void 四回までは止まらず_正しいパスワードでログインできる() throws Exception {
        String ip = "10.0.2.2";
        failTimes(EMAIL, ip, 4);

        login(EMAIL, PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void 成功すると回数が消える_四回失敗_成功_四回失敗でも止まらない() throws Exception {
        String ip = "10.0.2.3";
        failTimes(EMAIL, ip, 4);
        login(EMAIL, PASSWORD, ip).andExpect(status().isOk());
        failTimes(EMAIL, ip, 4);

        login(EMAIL, PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void 登録のないアドレスでも_同じ回数で_同じ429と文言になる() throws Exception {
        String ip = "10.0.2.4";
        failTimes(EMAIL, ip, 5);
        failTimes("login-limit-nobody@example.com", ip, 5);

        String registered = login(EMAIL, PASSWORD, ip).andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        String unregistered = login("login-limit-nobody@example.com", PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        assertThat(unregistered).isEqualTo(registered);
    }

    @Test
    void 止められるのは失敗した_IPアドレスからだけで_本人は別のIPアドレスからログインできる() throws Exception {
        failTimes(EMAIL, "10.0.2.5", 5);
        login(EMAIL, PASSWORD, "10.0.2.5").andExpect(status().isTooManyRequests());

        login(EMAIL, PASSWORD, "10.0.2.6").andExpect(status().isOk());
    }

    @Test
    void 同じIPアドレスでも_別のアドレスは止まらない() throws Exception {
        String ip = "10.0.2.7";
        failTimes(EMAIL, ip, 5);

        login(OTHER_EMAIL, PASSWORD, ip).andExpect(status().isOk());
    }

    // ---- ② IP アドレス ----

    @Test
    void アドレスを変えても_同じIPアドレスから30回失敗すると止まる() throws Exception {
        String ip = "10.0.2.8";
        // ①に掛からないよう、1つのアドレスでは4回まで
        for (int i = 0; i < 30; i++) {
            fail("login-limit-spray" + (i / 4) + "@example.com", ip);
        }

        login(OTHER_EMAIL, PASSWORD, ip).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(LIMIT_MESSAGE));
    }

    /**
     * ②は成功しても消さない（①と逆）。消すと、アカウントを1つ持っている攻撃者が、自分のアカウントにログインするだけで
     * IP アドレスの残り回数を戻せる（29回失敗 → 自分にログイン → また29回失敗 … が続けられる）。
     * あわせて、成功した1回は数えない（数えると、同じ IP アドレスを使う人たちが普通にログインするだけで止まる）
     */
    @Test
    void IPアドレスの回数は_成功しても消えず_成功した1回は数えない() throws Exception {
        String ip = "10.0.2.9";
        for (int i = 0; i < 29; i++) {
            fail("login-limit-spray" + (i / 4) + "@example.com", ip);
        }
        // 攻撃者が自分のアカウントにログインする
        login(OTHER_EMAIL, PASSWORD, ip).andExpect(status().isOk());

        // 成功は数えていないので、30回目の失敗はまだ照合される
        login("login-limit-spray-last@example.com", WRONG, ip).andExpect(status().isUnauthorized());
        // 成功で消えていないので、ここで止まる
        login(EMAIL, PASSWORD, ip).andExpect(status().isTooManyRequests());
    }

    // ---- パスワードの再設定との関係 ----

    /** 再設定を完了できるのはメールボックスを持つ本人なので、①を消しても攻撃者に何も渡さない。できたてのパスワードで締め出さない */
    @Test
    void 再設定が完了すると_そのアドレスの回数が消え_同じIPアドレスから新しいパスワードでログインできる() throws Exception {
        String ip = "10.0.2.11";
        failTimes(EMAIL, ip, 5);
        login(EMAIL, PASSWORD, ip).andExpect(status().isTooManyRequests());

        requestReset(EMAIL, ip);
        confirmReset(resetToken(), NEW_PASSWORD);

        login(EMAIL, NEW_PASSWORD, ip).andExpect(status().isOk());
    }

    /** ②は消さない。消すと、メールボックスを1つ持っている攻撃者が、再設定をするだけで IP アドレスの残り回数を戻せる */
    @Test
    void 再設定が完了しても_IPアドレスの回数は消えない() throws Exception {
        String ip = "10.0.2.12";
        for (int i = 0; i < 30; i++) {
            fail("login-limit-spray" + (i / 4) + "@example.com", ip);
        }
        login(EMAIL, PASSWORD, ip).andExpect(status().isTooManyRequests());

        requestReset(EMAIL, ip);
        confirmReset(resetToken(), NEW_PASSWORD);

        login(EMAIL, NEW_PASSWORD, ip).andExpect(status().isTooManyRequests());
    }

    /** メールを送っただけでは消さない（消すのは、メールボックスを持つことを示した「完了」のときだけ） */
    @Test
    void 再設定のメールを送っただけでは_そのアドレスの回数は消えない() throws Exception {
        String ip = "10.0.2.13";
        failTimes(EMAIL, ip, 5);

        requestReset(EMAIL, ip);
        assertThat(mailSender.sentTo(EMAIL)).hasSize(1);

        login(EMAIL, PASSWORD, ip).andExpect(status().isTooManyRequests());
    }

    // ---- 記録 ----

    @Test
    void アプリのログに_メールアドレスとIPアドレスを書かない(AppLog appLog) throws Exception {
        String ip = "10.0.2.10";
        failTimes(EMAIL, ip, 5);
        login(EMAIL, WRONG, ip).andExpect(status().isTooManyRequests());

        assertThat(appLog.text()).doesNotContain(EMAIL, ip);
    }

    // ---- 部品 ----

    private void failTimes(String email, String ip, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            fail(email, ip);
        }
    }

    /** 失敗させる（まだ止まっていないので、照合されて 401 になること） */
    private void fail(String email, String ip) throws Exception {
        login(email, WRONG, ip).andExpect(status().isUnauthorized());
    }

    private void requestReset(String email, String ip) throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(req -> {
                            req.setRemoteAddr(ip);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());
    }

    private void confirmReset(String token, String newPassword) throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("token", token, "newPassword", newPassword))))
                .andExpect(status().isNoContent());
    }

    /** 再設定のメールの鍵（1通だけ届いていること） */
    private String resetToken() {
        List<OutgoingMail> mails = mailSender.sentTo(EMAIL);
        assertThat(mails).hasSize(1);
        return RecordingMailSender.tokenOf(mails.get(0));
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(req -> {
                    req.setRemoteAddr(ip);
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password))));
    }
}
