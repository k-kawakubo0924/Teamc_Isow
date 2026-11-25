package com.teamc.isow.backend.moderation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.image.ImageTestSupport;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * 措置のテストで使う登場人物と操作（docs/admin.md「6. 措置」）。
 * author が措置を受ける人、viewer がほかの利用者、admin が管理者。パスワードは全員 {@link #PASSWORD}
 */
final class ModerationFixture {

    static final String EMAIL_PATTERN = "mod-%@example.com";
    static final String PASSWORD = "password123";

    final MockMvc mockMvc;
    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FashionCategoryRepository fashionCategoryRepository;
    private final TagRepository tagRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    User author;
    User viewer;
    User admin;

    ModerationFixture(MockMvc mockMvc, JwtEncoder jwtEncoder, UserRepository userRepository,
            PostRepository postRepository, FashionCategoryRepository fashionCategoryRepository,
            TagRepository tagRepository, JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.fashionCategoryRepository = fashionCategoryRepository;
        this.tagRepository = tagRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    void createUsers() {
        String hash = passwordEncoder.encode(PASSWORD);
        author = userRepository.save(new User("mod-author@example.com", "09000000210", hash, "mod_author"));
        viewer = userRepository.save(new User("mod-viewer@example.com", "09000000211", hash, "mod_viewer"));
        admin = userRepository.save(new User("mod-admin@example.com", "09000000212", hash, "mod_admin"));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    void cleanUp() {
        jdbcTemplate.update("DELETE FROM moderation_actions");
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
        jdbcTemplate.update("DELETE FROM report_evidence_files");
        jdbcTemplate.update("DELETE FROM reports");
        jdbcTemplate.update("DELETE FROM report_cases");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversation_user_settings");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM likes");
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM search_histories");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PATTERN);
    }

    Post createPost(User owner, String title) {
        return postRepository.save(new Post(owner, title,
                fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0), null, title + "の説明",
                null, List.of(ImageTestSupport.key(title + ".jpg")),
                List.of(tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0))));
    }

    /** 投稿を非公開にした状態にする（管理者の API を通さず、DB に直接書く） */
    void hide(Post post) {
        jdbcTemplate.update("UPDATE posts SET hidden_at = ?, hidden_reason = 'SPAM' WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), post.getId());
    }

    /** 利用停止にした状態にする（until が null なら無期限。DB に直接書く） */
    void suspend(User user, LocalDateTime until) {
        jdbcTemplate.update("UPDATE users SET suspended_at = ?, suspended_until = ?, suspension_reason = 'HARASSMENT' "
                        + "WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                until == null ? null : Timestamp.valueOf(until), user.getId());
    }

    void follow(User from, User to) throws Exception {
        as(from, post("/api/users/" + to.getId() + "/follow")).andExpect(status().isOk());
    }

    /** from が to に相談を申し込み、承認まで進めた会話の ID */
    long activeConversation(User from, User to) throws Exception {
        String body = as(from, post("/api/conversations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\": " + to.getId() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.conversationId")).longValue();
        as(to, post("/api/conversations/" + id + "/accept")).andExpect(status().isOk());
        return id;
    }

    /** from が to に相談を申し込んだ（申請中）会話の ID */
    long requestedConversation(User from, User to) throws Exception {
        String body = as(from, post("/api/conversations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\": " + to.getId() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.conversationId")).longValue();
    }

    <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions as(User user, B request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(user)));
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

    long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
