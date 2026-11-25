package com.teamc.isow.backend.auth;

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
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * パスワードの再設定（ログイン前。docs/auth.md「パスワードの再設定」）。
 *
 * <ul>
 *   <li>登録の有無にかかわらず、同じ 202 と同じ文言を返す（登録済みかどうかを外から調べられないように）</li>
 *   <li>リンクの鍵は purpose=password_reset の JWT。30分で切れ、トークンの世代を入れて使い捨てにする</li>
 *   <li>再設定するとトークンの世代を進め、すべての端末をログアウトさせる。「パスワードが変更されました」と知らせる</li>
 *   <li>同じアカウントには5分に1通まで。IP アドレスごとの回数制限はメモリの上だけ（テストでは3回まで）</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(AppLogExtension.class)
class PasswordResetApiTest {

    private static final String EMAIL = "pw-reset-user@example.com";
    private static final String PASSWORD = "password123";
    private static final String NEW_PASSWORD = "newpass456";
    private static final String ACCEPTED_MESSAGE =
            "入力したメールアドレスが登録されていれば、パスワード再設定のメールを送りました。届かない場合は、時間をおいてもう一度お試しください。";
    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @Autowired
    private RecordingMailSender mailSender;

    private User user;

    @BeforeEach
    void setUp() {
        mailSender.clear();
        user = userRepository.save(new User(EMAIL, "09000000901", passwordEncoder.encode(PASSWORD), "pw_reset_user"));
    }

    @AfterEach
    void tearDown() {
        mailSender.clear();
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "pw-reset-%@example.com");
    }

    // ---- 申し込み ----

    @Test
    void 登録済みなら再設定のリンクを送り_本文には用件_リンク_期限_ユーザー名を書く() throws Exception {
        request(EMAIL, "10.0.1.1").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(ACCEPTED_MESSAGE));

        OutgoingMail mail = onlyMailTo(EMAIL);
        assertThat(mail.subject()).isEqualTo("【ISHO】パスワードの再設定");
        assertThat(RecordingMailSender.firstLink(mail)).startsWith("http://localhost:5173/reset-password#token=");
        assertThat(mail.body()).contains("@pw_reset_user", "30分", "心当たりがない場合");
        assertThat(mail.body()).doesNotContain("09000000901", PASSWORD);
    }

    @Test
    void 登録されていなくても同じ応答を返し_メールは送らない() throws Exception {
        String registered = request(EMAIL, "10.0.1.2").andReturn().getResponse().getContentAsString();
        String unknown = request("pw-reset-nobody@example.com", "10.0.1.2")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(registered);
        assertThat(mailSender.sentTo("pw-reset-nobody@example.com")).isEmpty();
    }

    @Test
    void メールアドレスは前後の空白と大文字小文字をそろえて探す() throws Exception {
        request("  PW-Reset-User@Example.com ", "10.0.1.3").andExpect(status().isAccepted());

        assertThat(mailSender.sentTo(EMAIL)).hasSize(1);
    }

    @Test
    void 形式の誤りだけは400にする_登録の有無とは関係ないため() throws Exception {
        request("", "10.0.1.4").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        request("not-an-address", "10.0.1.4").andExpect(status().isBadRequest());
    }

    @Test
    void 同じアカウントには5分に1通まで_超えた分は同じ応答で送らない() throws Exception {
        request(EMAIL, "10.0.1.5").andExpect(status().isAccepted());
        request(EMAIL, "10.0.1.6").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(ACCEPTED_MESSAGE));
        assertThat(mailSender.sentTo(EMAIL)).hasSize(1);

        jdbcTemplate.update("UPDATE users SET password_reset_requested_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(6)), user.getId());
        request(EMAIL, "10.0.1.7").andExpect(status().isAccepted());
        assertThat(mailSender.sentTo(EMAIL)).hasSize(2);
    }

    @Test
    void 同じIPアドレスからは回数に上限があり_超えた分は同じ応答で送らない() throws Exception {
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.update("UPDATE users SET password_reset_requested_at = NULL WHERE id = ?", user.getId());
            request(EMAIL, "10.0.2.1").andExpect(status().isAccepted());
        }
        jdbcTemplate.update("UPDATE users SET password_reset_requested_at = NULL WHERE id = ?", user.getId());
        request(EMAIL, "10.0.2.1").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(ACCEPTED_MESSAGE));

        assertThat(mailSender.sentTo(EMAIL)).hasSize(3);
        // 別の IP アドレスからは送れる
        request(EMAIL, "10.0.2.2").andExpect(status().isAccepted());
        assertThat(mailSender.sentTo(EMAIL)).hasSize(4);
    }

    @Test
    void 利用停止中でも再設定できる_ログインすると利用停止の画面に進める() throws Exception {
        jdbcTemplate.update("UPDATE users SET suspended_at = ?, suspension_reason = 'SPAM' WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)), user.getId());
        request(EMAIL, "10.0.1.8").andExpect(status().isAccepted());

        confirm(RecordingMailSender.tokenOf(onlyMailTo(EMAIL)), NEW_PASSWORD).andExpect(status().isNoContent());

        login(NEW_PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.reason").value("ACCOUNT_SUSPENDED"));
    }

    // ---- 再設定 ----

    @Test
    void 再設定すると新しいパスワードでログインでき_それまでのトークンは使えず_変更を知らせる() throws Exception {
        String oldLoginToken = JsonPath.read(login(PASSWORD).andReturn().getResponse().getContentAsString(), "$.token");
        request(EMAIL, "10.0.1.9").andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(EMAIL));

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login(PASSWORD).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldLoginToken))
                .andExpect(status().isUnauthorized());
        List<OutgoingMail> mails = mailSender.sentTo(EMAIL);
        assertThat(mails).hasSize(2);
        assertThat(mails.get(1).subject()).isEqualTo("【ISHO】パスワードが変更されました");
        assertThat(mails.get(1).body()).contains("@pw_reset_user", "パスワードをお忘れですか？");
    }

    @Test
    void 鍵は使い捨て_先に送った鍵も_1つ使えば全部使えなくなる() throws Exception {
        request(EMAIL, "10.0.1.10").andExpect(status().isAccepted());
        String first = RecordingMailSender.tokenOf(onlyMailTo(EMAIL));
        jdbcTemplate.update("UPDATE users SET password_reset_requested_at = NULL WHERE id = ?", user.getId());
        request(EMAIL, "10.0.1.10").andExpect(status().isAccepted());
        String second = RecordingMailSender.tokenOf(mailSender.sentTo(EMAIL).get(1));

        confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());

        expectInvalidLink(confirm(second, "another789"));
        expectInvalidLink(confirm(first, "another789"));
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 期限切れ_ほかの用途_ログイン用のトークン_壊れた鍵は使えない() throws Exception {
        String expired = resetToken(Instant.now().minus(Duration.ofMinutes(10)), "password_reset");
        String otherPurpose = resetToken(Instant.now().plus(Duration.ofMinutes(10)), "suspension_appeal");
        String loginToken = JsonPath.read(login(PASSWORD).andReturn().getResponse().getContentAsString(), "$.token");

        for (String token : new String[] {expired, otherPurpose, loginToken, "not-a-token"}) {
            expectInvalidLink(confirm(token, NEW_PASSWORD));
        }
        login(PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 再設定の鍵で普通のAPIは使えない() throws Exception {
        request(EMAIL, "10.0.1.11").andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(EMAIL));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void 新しいパスワードは登録と同じ規則_満たさなければ400で鍵は使える状態のまま() throws Exception {
        request(EMAIL, "10.0.1.12").andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(EMAIL));

        confirm(token, "short1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("8文字以上、英字と数字を含めてください"));
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void メールアドレス_鍵_リンクはアプリのログに書かない(AppLog appLog) throws Exception {
        request(EMAIL, "10.0.1.13").andExpect(status().isAccepted());
        request("pw-reset-nobody@example.com", "10.0.1.13").andExpect(status().isAccepted());
        String token = RecordingMailSender.tokenOf(onlyMailTo(EMAIL));
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(appLog.text()).doesNotContain(EMAIL, "pw-reset-nobody", token, "reset-password#");
    }

    // ---- 部品 ----

    private ResultActions request(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/request")
                .with(req -> {
                    req.setRemoteAddr(ip);
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", EMAIL, "password", password))));
    }

    private static void expectInvalidLink(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "このリンクは使えません。有効期限（30分）が切れたか、すでに使われています。もう一度、再設定のメールを送ってください。"));
    }

    private OutgoingMail onlyMailTo(String to) {
        List<OutgoingMail> mails = mailSender.sentTo(to);
        assertThat(mails).hasSize(1);
        return mails.get(0);
    }

    private String resetToken(Instant expiresAt, String purpose) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(expiresAt.minus(Duration.ofMinutes(30)))
                .expiresAt(expiresAt)
                .claim("purpose", purpose)
                .claim("ver", 0)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
