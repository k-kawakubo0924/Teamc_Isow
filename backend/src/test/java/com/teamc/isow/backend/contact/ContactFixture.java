package com.teamc.isow.backend.contact;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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
 * お問い合わせのテストで使う登場人物と操作（docs/settings.md「お問い合わせ」）。
 * sender が送る人、other がほかの利用者、admin が管理者。パスワードは全員 {@link #PASSWORD}
 */
final class ContactFixture {

    static final String EMAIL_PATTERN = "contact-%@example.com";
    static final String SENDER_EMAIL = "contact-sender@example.com";
    static final String PASSWORD = "password123";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    final MockMvc mockMvc;
    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    User sender;
    User other;
    User admin;

    ContactFixture(MockMvc mockMvc, JwtEncoder jwtEncoder, UserRepository userRepository, JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    void createUsers() {
        String hash = passwordEncoder.encode(PASSWORD);
        sender = userRepository.save(new User(SENDER_EMAIL, "09000000310", hash, "contact_sender"));
        other = userRepository.save(new User("contact-other@example.com", "09000000311", hash, "contact_other"));
        admin = userRepository.save(new User("contact-admin@example.com", "09000000312", hash, "contact_admin"));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    void cleanUp() {
        jdbcTemplate.update("DELETE FROM contact_inquiries");
        jdbcTemplate.update("DELETE FROM moderation_actions");
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PATTERN);
    }

    /** 利用停止にした状態にする（無期限。DB に直接書く） */
    void suspend(User user) {
        jdbcTemplate.update("UPDATE users SET suspended_at = ?, suspended_until = NULL, suspension_reason = 'HARASSMENT' "
                + "WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusDays(1)), user.getId());
    }

    /** ログイン中の人が送る（POST /api/contacts） */
    ResultActions send(User user, String email, String category, String body) throws Exception {
        return mockMvc.perform(post("/api/contacts").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", nullToEmpty(email), "category", category, "body", body))));
    }

    /** 利用停止中の人が申し立てる（POST /api/contacts/appeal。トークンは付けない） */
    ResultActions appeal(String appealToken, String email, String category, String body) throws Exception {
        return mockMvc.perform(post("/api/contacts/appeal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("appealToken", appealToken, "email", nullToEmpty(email), "category", category,
                        "body", body))));
    }

    /** 利用停止中の人がログインし、403 の応答から申し立て用の鍵を受け取る */
    String appealTokenFromLogin(User user) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.appealToken");
    }

    ResultActions getAs(User user, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(user)));
    }

    ResultActions postAs(User user, String path, String jsonBody) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(jsonBody));
    }

    String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /**
     * 申し立て用の鍵と同じ形で、purpose・期限を変えたトークン（期限切れ・別の purpose の確認に使う）。
     * 結びつける利用停止は、今の DB の値にする（利用停止中でなければ付けない）
     */
    String purposeToken(User user, String purpose, Instant expiresAt) {
        Timestamp suspendedAt = jdbcTemplate.queryForObject("SELECT suspended_at FROM users WHERE id = ?",
                Timestamp.class, user.getId());
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(expiresAt.minus(Duration.ofHours(1)))
                .expiresAt(expiresAt)
                .claim("purpose", purpose);
        if (suspendedAt != null) {
            builder.claim("suspendedAt", suspendedAt.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS).toString());
        }
        JwtClaimsSet claims = builder.build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    long latestId() {
        return count("SELECT max(id) FROM contact_inquiries");
    }

    static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
