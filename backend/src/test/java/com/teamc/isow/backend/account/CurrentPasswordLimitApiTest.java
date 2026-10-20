package com.teamc.isow.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.mail.RecordingMailSender;
import com.teamc.isow.backend.user.UserRepository;
import com.teamc.isow.backend.user.User;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * 現在のパスワードの確認の失敗の回数の上限（docs/auth.md「ログインの失敗の回数の上限」の③）。
 *
 * <ul>
 *   <li>ユーザーごとに、15分に5回まで。メールアドレスの変更とログイン中のパスワードの変更で<b>合わせて</b>数える
 *       （分けると、交互に試すだけで2倍試せる）</li>
 *   <li>止まっている間は、パスワードを照合せずに 429（正しいパスワードでも通さない）。成功したら消す</li>
 *   <li>上限に達してもログアウトさせない。メールでも知らせない</li>
 * </ul>
 * 回数はメモリの上で数え、テストの間で残るが、ユーザーはテストごとに作り直す（ID が変わる）ので混ざらない
 */
@SpringBootTest
@AutoConfigureMockMvc
class CurrentPasswordLimitApiTest {

    private static final String EMAIL = "current-pw-user@example.com";
    private static final String OTHER_EMAIL = "current-pw-other@example.com";
    private static final String PASSWORD = "password123";
    private static final String WRONG = "wrongpass999";
    private static final String NEW_PASSWORD = "newpass456";
    private static final String LIMIT_MESSAGE =
            "現在のパスワードの誤りが続いたため、一時的に受け付けを止めています。あと約15分で、もう一度お試しください。";
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

    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        mailSender.clear();
        userRepository.save(new User(EMAIL, "09000000961", passwordEncoder.encode(PASSWORD), "current_pw_user"));
        userRepository.save(new User(OTHER_EMAIL, "09000000962", passwordEncoder.encode(PASSWORD), "current_pw_other"));
        bearer = loginToken(EMAIL);
    }

    @AfterEach
    void tearDown() {
        mailSender.clear();
        jdbcTemplate.update("DELETE FROM email_change_requests");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "current-pw-%@example.com");
    }

    @Test
    void メールアドレスの変更で五回誤ると_六回目は正しいパスワードでも429で_あと約N分とRetry_Afterを返す() throws Exception {
        for (int i = 0; i < 5; i++) {
            failEmailChange(bearer);
        }

        String retryAfter = emailChange(bearer, PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(LIMIT_MESSAGE))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(15 * 60 - 60, 15 * 60);
    }

    @Test
    void パスワードの変更で五回誤ると_六回目は正しいパスワードでも429になる() throws Exception {
        for (int i = 0; i < 5; i++) {
            failPasswordChange(bearer);
        }

        passwordChange(bearer, PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(LIMIT_MESSAGE));
    }

    @Test
    void 二か所の失敗は合わせて数える() throws Exception {
        for (int i = 0; i < 3; i++) {
            failEmailChange(bearer);
        }
        for (int i = 0; i < 2; i++) {
            failPasswordChange(bearer);
        }

        emailChange(bearer, PASSWORD).andExpect(status().isTooManyRequests());
        passwordChange(bearer, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void 成功すると回数が消える_四回誤り_成功_四回誤りでも止まらない() throws Exception {
        for (int i = 0; i < 4; i++) {
            failEmailChange(bearer);
        }
        emailChange(bearer, PASSWORD).andExpect(status().isAccepted());
        for (int i = 0; i < 4; i++) {
            failEmailChange(bearer);
        }

        emailChange(bearer, PASSWORD).andExpect(status().isAccepted());
    }

    @Test
    void ほかのユーザーは止まらない() throws Exception {
        for (int i = 0; i < 5; i++) {
            failPasswordChange(bearer);
        }

        passwordChange(loginToken(OTHER_EMAIL), PASSWORD).andExpect(status().isOk());
    }

    /** ログアウトさせない（docs/auth.md：自分で5回打ち間違えただけで全端末から追い出されるのは割に合わない） */
    @Test
    void 上限に達しても_ログアウトはさせない() throws Exception {
        for (int i = 0; i < 5; i++) {
            failPasswordChange(bearer);
        }
        passwordChange(bearer, PASSWORD).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bearer)).andExpect(status().isOk());
    }

    /** メールで知らせない（docs/auth.md：知らせると、わざと失敗し続けることで本人の受信箱を埋められる） */
    @Test
    void 上限に達しても_メールでは知らせない() throws Exception {
        for (int i = 0; i < 5; i++) {
            failPasswordChange(bearer);
        }
        passwordChange(bearer, PASSWORD).andExpect(status().isTooManyRequests());
        emailChange(bearer, PASSWORD).andExpect(status().isTooManyRequests());

        assertThat(mailSender.sent()).isEmpty();
    }

    // ---- 部品 ----

    /** 現在のパスワードを誤る（まだ止まっていないので、照合されて 400 になること） */
    private void failEmailChange(String token) throws Exception {
        emailChange(token, WRONG).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("現在のパスワードが正しくありません"));
    }

    private void failPasswordChange(String token) throws Exception {
        passwordChange(token, WRONG).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("現在のパスワードが正しくありません"));
    }

    private ResultActions emailChange(String token, String currentPassword) throws Exception {
        return mockMvc.perform(post("/api/users/me/email-change").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("currentPassword", currentPassword,
                        "newEmail", "current-pw-new@example.com", "confirmEmail", "current-pw-new@example.com"))));
    }

    private ResultActions passwordChange(String token, String currentPassword) throws Exception {
        return mockMvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", NEW_PASSWORD))));
    }

    private String loginToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
