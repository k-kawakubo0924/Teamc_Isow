package com.teamc.isow.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/**
 * トークンの世代（users.token_version。docs/auth.md「トークンの世代（全端末のログアウト）」）。
 * パスワードの再設定・変更、メールアドレスの変更で世代を進めると、それより前に発行したログイン用のトークンは 401 になる。
 * 世代の入っていないトークン（この仕組みの前に発行したもの）は世代0として扱う
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenVersionTest {

    private static final String EMAIL = "token-version@example.com";
    private static final String PASSWORD = "password123";

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

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User(EMAIL, "09000000801", passwordEncoder.encode(PASSWORD), "token_version"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", EMAIL);
    }

    @Test
    void ログインのトークンには世代が入り_世代を進めると401になり_ログインし直せば使える() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET token_version = token_version + 1 WHERE id = ?", user.getId());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts?tab=latest").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + login())).andExpect(status().isOk());
    }

    @Test
    void 世代の入っていないトークンは世代0として扱う() throws Exception {
        String legacy = tokenWithoutVersion();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + legacy)).andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET token_version = 1 WHERE id = ?", user.getId());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + legacy)).andExpect(status().isUnauthorized());
    }

    @Test
    void 世代が古いトークンは_利用停止中でも403ではなく401にする() throws Exception {
        String token = login();
        jdbcTemplate.update("UPDATE users SET token_version = 1, suspended_at = CURRENT_TIMESTAMP - INTERVAL '1' DAY, "
                + "suspension_reason = 'SPAM' WHERE id = ?", user.getId());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String tokenWithoutVersion() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
