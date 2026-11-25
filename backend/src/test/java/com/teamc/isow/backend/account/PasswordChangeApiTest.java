package com.teamc.isow.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.common.AppLog;
import com.teamc.isow.backend.common.AppLogExtension;
import com.teamc.isow.backend.mail.OutgoingMail;
import com.teamc.isow.backend.mail.RecordingMailSender;
import com.teamc.isow.backend.user.UserRepository;
import com.teamc.isow.backend.user.User;
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
 * ログイン中のパスワードの変更（docs/settings.md「パスワード再設定（ログイン中の変更）」、design/ResetPassword.png）。
 * 現在のパスワードを求める。変更するとトークンの世代を進めてすべての端末をログアウトさせ、操作した端末には新しいトークンを返す。
 * 登録アドレスに「パスワードが変更されました」と知らせる
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(AppLogExtension.class)
class PasswordChangeApiTest {

    private static final String EMAIL = "pw-change-user@example.com";
    private static final String PASSWORD = "password123";
    private static final String NEW_PASSWORD = "newpass456";
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

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mailSender.clear();
        userRepository.save(new User(EMAIL, "09000000961", passwordEncoder.encode(PASSWORD), "pw_change_user"));
        token = loginToken(PASSWORD);
    }

    @AfterEach
    void tearDown() {
        mailSender.clear();
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", EMAIL);
    }

    @Test
    void 変更するとほかの端末はログアウトになり_操作した端末には新しいトークンを返し_変更を知らせる() throws Exception {
        String otherDevice = loginToken(PASSWORD);

        String body = change(token, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(body, "$.token");

        me(otherDevice).andExpect(status().isUnauthorized());
        me(token).andExpect(status().isUnauthorized());
        me(newToken).andExpect(status().isOk());
        login(PASSWORD).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD).andExpect(status().isOk());
        List<OutgoingMail> mails = mailSender.sentTo(EMAIL);
        assertThat(mails).singleElement().satisfies(mail ->
                assertThat(mail.subject()).isEqualTo("【ISHO】パスワードが変更されました"));
    }

    @Test
    void 現在のパスワードが違えば400で何も変えない() throws Exception {
        change(token, "wrongpass1", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("現在のパスワードが正しくありません"));

        me(token).andExpect(status().isOk());
        login(PASSWORD).andExpect(status().isOk());
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    void 新しいパスワードは登録と同じ規則で_今と同じものは400() throws Exception {
        change(token, PASSWORD, "short1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("8文字以上、英字と数字を含めてください"));
        change(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("今のパスワードと同じです"));
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    void トークンなしは401() throws Exception {
        mockMvc.perform(post("/api/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void パスワードはアプリのログに書かない(AppLog appLog) throws Exception {
        change(token, "wrongpass1", NEW_PASSWORD).andExpect(status().isBadRequest());
        change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        assertThat(appLog.text()).doesNotContain(PASSWORD, NEW_PASSWORD, "wrongpass1", EMAIL);
    }

    private ResultActions change(String bearer, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/users/me/password").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
    }

    private ResultActions me(String bearer) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bearer));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", EMAIL, "password", password))));
    }

    private String loginToken(String password) throws Exception {
        return JsonPath.read(login(password).andReturn().getResponse().getContentAsString(), "$.token");
    }
}
