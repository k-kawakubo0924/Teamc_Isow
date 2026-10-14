package com.teamc.isow.backend.block;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * ブロックのテストで使う登場人物と操作（docs/profile.md「ブロック」）。
 * A がブロックする人、B がブロックされる人、C はどちらとも関係のない第三者。
 * ユーザー名・投稿の題名は、応答の本文に含まれていないかを文字列で確かめられるよう、ほかと重ならない値にしている
 */
final class BlockFixture {

    static final String EMAIL_PATTERN = "blk-%@example.com";

    final MockMvc mockMvc;
    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FashionCategoryRepository fashionCategoryRepository;
    private final TagRepository tagRepository;
    private final JdbcTemplate jdbcTemplate;

    User a;
    User b;
    User c;

    BlockFixture(MockMvc mockMvc, JwtEncoder jwtEncoder, UserRepository userRepository, PostRepository postRepository,
            FashionCategoryRepository fashionCategoryRepository, TagRepository tagRepository, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.fashionCategoryRepository = fashionCategoryRepository;
        this.tagRepository = tagRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    void createUsers() {
        a = userRepository.save(new User("blk-a@example.com", "09000000190", "hash", "blkuser_aaa"));
        b = userRepository.save(new User("blk-b@example.com", "09000000191", "hash", "blkuser_bbb"));
        c = userRepository.save(new User("blk-c@example.com", "09000000192", "hash", "blkuser_ccc"));
    }

    /** テストで作ったものを消す（参照している側から消す。ブロックの行はユーザーの削除で一緒に消える） */
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM messages");
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

    /** 投稿を作る（写真1枚・公式タグ1つ）。題名は応答に含まれていないかの確認に使う */
    Post createPost(User author, String title) {
        return postRepository.save(new Post(author, title,
                fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0), null,
                title + "の説明", null,
                List.of("http://localhost/uploads/" + title + ".jpg"),
                List.of(tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0))));
    }

    // ---- API の操作（画面と同じ API で作る） ----

    void follow(User from, User to) throws Exception {
        as(from, post("/api/users/" + to.getId() + "/follow")).andExpect(status().isOk());
    }

    void like(User user, Post post) throws Exception {
        as(user, post("/api/posts/" + post.getId() + "/like")).andExpect(status().is2xxSuccessful());
    }

    void favorite(User user, Post post) throws Exception {
        as(user, post("/api/posts/" + post.getId() + "/favorite")).andExpect(status().is2xxSuccessful());
    }

    /** 相談を申し込み、会話の ID を返す */
    long requestConsultation(User from, User to) throws Exception {
        String body = as(from, post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\": " + to.getId() + ", \"message\": \"よろしくお願いします\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.conversationId")).longValue();
    }

    void accept(User user, long conversationId) throws Exception {
        as(user, post("/api/conversations/" + conversationId + "/accept")).andExpect(status().isOk());
    }

    ResultActions block(User from, User to) throws Exception {
        return as(from, post("/api/users/" + to.getId() + "/block"));
    }

    ResultActions unblock(User from, User to) throws Exception {
        return as(from, delete("/api/users/" + to.getId() + "/block"));
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
