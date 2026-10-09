package com.teamc.isow.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * SecurityConfig の許可・拒否の確認。
 * 誤って全許可（anyRequest().permitAll() など）にした場合に、ここで気づけるようにする。
 * DB は src/test/resources/application.properties の H2 を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final String EMAIL = "security-test@example.com";
    /** 管理者向けの API の確認に使うユーザー（テストごとに作り直し、終わったら消す） */
    private static final String ADMIN_EMAIL = "security-admin@example.com";
    private static final String PASSWORD = "abc12345";
    /** src/test/resources/application.properties の app.cors.allowed-origin と同じ値 */
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.findByEmail(EMAIL).orElseGet(() -> userRepository.save(
                new User(EMAIL, "09012345678", passwordEncoder.encode(PASSWORD), "security_test")));
    }

    @Test
    void 疎通確認は認証なしで許可される() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void ログインは認証なしで許可される() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void 新規会員登録は認証なしで許可される() throws Exception {
        // 入力チェックで 400 になる＝認証を通過してコントローラーまで届いている
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 許可したURLでもメソッドが違えば認証が必要() throws Exception {
        mockMvc.perform(delete("/api/auth/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void トークンなしでは認証が必要なAPIは401() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
    }

    @Test
    void 不正なトークンは401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 期限切れのトークンは401() throws Exception {
        // 検証時の時計のずれの許容（60秒）を超えて過去に切れたトークン
        Instant issuedAt = Instant.now().minus(Duration.ofHours(25));
        String token = token(String.valueOf(user.getId()), issuedAt, issuedAt.plus(Duration.ofHours(24)));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 存在しないユーザーのトークンは401() throws Exception {
        Instant now = Instant.now();
        String token = token("999999", now, now.plus(Duration.ofHours(1)));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 正しいトークンなら本人の情報が返りパスワードは含まれない() throws Exception {
        Instant now = Instant.now();
        String token = token(String.valueOf(user.getId()), now, now.plus(Duration.ofHours(1)));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.username").value("security_test"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString(user.getPasswordHash()))));
    }

    @Test
    void 許可したオリジンからのログインのプリフライトは通る() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type"));
    }

    @Test
    void 許可していないオリジンからのプリフライトは拒否される() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---- 管理者向けの API（/api/admin/**。docs/admin.md「権限の仕組み」） ----

    @Test
    void 管理者向けのAPIはトークンなしなら401() throws Exception {
        mockMvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/does-not-exist")).andExpect(status().isUnauthorized());
    }

    @Test
    void 管理者向けのAPIは一般の利用者なら_存在しないURLと同じ404() throws Exception {
        String token = validToken(adminCandidate("USER"));

        MvcResult admin = mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();
        // 管理者向けの API があることが分からないよう、存在しない URL を開いたときと見分けが付かないこと
        MvcResult unknownAdmin = mockMvc.perform(
                        get("/api/admin/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult unknown = mockMvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();
        // 利用者に見える部分（状態コード・本文・本文の形式）が同じこと。
        // 存在しない URL ではサーバー内部のエラーの文（getErrorMessage）が付くが、Spring Boot の既定
        // （server.error.include-message=never）で応答の本文には入らないため、比べない
        for (MvcResult other : List.of(unknownAdmin, unknown)) {
            assertThat(admin.getResponse().getStatus()).isEqualTo(other.getResponse().getStatus());
            assertThat(admin.getResponse().getContentAsString()).isEqualTo(other.getResponse().getContentAsString());
            assertThat(admin.getResponse().getContentType()).isEqualTo(other.getResponse().getContentType());
        }
    }

    @Test
    void 管理者向けのAPIは管理者なら通る() throws Exception {
        User admin = adminCandidate("ADMIN");

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + validToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.username").value("security_admin"));
    }

    /** role は NOT NULL のため、NULL は User の単体テストで確かめる。ここでは DB に入りうる想定外の値を確かめる */
    @ParameterizedTest
    @ValueSource(strings = {"admin", "Admin", "SUPER_ADMIN", " ADMIN", ""})
    void roleが想定外の値なら管理者として扱わず404(String role) throws Exception {
        User user = adminCandidate(role);

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + validToken(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 管理者から一般の利用者に戻した人は_戻す前のトークンでも404() throws Exception {
        User admin = adminCandidate("ADMIN");
        String token = validToken(admin);
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        // トークンには権限を入れず、リクエストのたびに DB で確かめているため、すぐに使えなくなる
        jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.getId());

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 管理者の規則を足しても_一般のAPIは一般の利用者も管理者も今までどおり使える() throws Exception {
        for (String role : List.of("USER", "ADMIN")) {
            User user = adminCandidate(role);
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + validToken(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId()));
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());
        }
    }

    @AfterEach
    void deleteAdminCandidate() {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", ADMIN_EMAIL);
    }

    /** 管理者の確認用のユーザーを作り、role を DB に直接書き込む（想定外の値も入れられるように） */
    private User adminCandidate(String role) {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", ADMIN_EMAIL);
        User created = userRepository.save(
                new User(ADMIN_EMAIL, "09087654321", passwordEncoder.encode(PASSWORD), "security_admin"));
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role, created.getId());
        return created;
    }

    private String validToken(User target) {
        Instant now = Instant.now();
        return token(String.valueOf(target.getId()), now, now.plus(Duration.ofHours(1)));
    }

    private String token(String subject, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
