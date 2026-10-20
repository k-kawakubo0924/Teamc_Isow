package com.teamc.isow.backend.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * 非公開にした投稿を、投稿者本人が見るときの写真の扱い（docs/admin.md「6. 措置」の「非公開にした投稿の写真」）。
 * 非公開にした投稿の写真は、本人にも配らない（PublicImageAccessTest）。そのため、本人に返す応答にも写真の URL を入れない
 * （入れると、画面に壊れた画像が出る）。画面は、写真の代わりに「写真は運営により非公開にしています」を出す。
 *
 * <ul>
 *   <li>詳細：hidden が true で、imageUrls は空</li>
 *   <li>一覧のカード・通知：thumbnailUrl が null</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class HiddenPostImageResponseTest {

    private static final String EMAIL_PATTERN = "himg-%@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User author;
    private User admin;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("himg-author@example.com", "09000000324", "hash", "himg_author"));
        admin = userRepository.save(new User("himg-admin@example.com", "09000000325", "hash", "himg_admin"));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM moderation_actions");
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM tags WHERE is_official = false");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PATTERN);
    }

    @Test
    void 本人が非公開の投稿の詳細を開くと_hiddenがtrueで_写真のURLは返さない() throws Exception {
        long postId = createPost(2);
        hide(postId);

        String body = as(author, get("/api/posts/" + postId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((Boolean) JsonPath.read(body, "$.hidden")).isTrue();
        assertThat((List<?>) JsonPath.read(body, "$.imageUrls")).isEmpty();
    }

    @Test
    void 公開に戻すと_詳細に写真のURLがまた返る() throws Exception {
        long postId = createPost(2);
        hide(postId);
        as(admin, post("/api/admin/moderation/posts/" + postId + "/unhide").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        String body = as(author, get("/api/posts/" + postId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((Boolean) JsonPath.read(body, "$.hidden")).isFalse();
        assertThat((List<?>) JsonPath.read(body, "$.imageUrls")).hasSize(2);
    }

    @Test
    void 本人のプロフィールの投稿一覧では_非公開の投稿のサムネイルだけがnull() throws Exception {
        long hidden = createPost(1);
        long visible = createPost(1);
        hide(hidden);

        String body = as(author, get("/api/users/" + author.getId() + "/posts")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Object> hiddenThumbnails = JsonPath.read(body, "$.posts[?(@.id == " + hidden + ")].thumbnailUrl");
        assertThat(hiddenThumbnails).hasSize(1).containsOnlyNulls();
        List<Object> visibleThumbnails = JsonPath.read(body, "$.posts[?(@.id == " + visible + ")].thumbnailUrl");
        assertThat(visibleThumbnails).singleElement().asString().contains("/uploads/");
    }

    @Test
    void 非公開にしたことの通知では_投稿のサムネイルがnull() throws Exception {
        long postId = createPost(1);
        hide(postId);

        String body = as(author, get("/api/notifications")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Object> thumbnails = JsonPath.read(body, "$.notifications[?(@.post.id == " + postId + ")].post.thumbnailUrl");
        assertThat(thumbnails).isNotEmpty().containsOnlyNulls();
    }

    // ---- 部品 ----

    private long createPost(int photos) throws Exception {
        var request = multipart("/api/posts");
        for (int i = 0; i < photos; i++) {
            request.file(new MockMultipartFile("images", i + ".png", "image/png", png()));
        }
        request.param("title", "非公開の写真の確認")
                .param("fashionCategoryId", String.valueOf(jdbcTemplate.queryForObject(
                        "SELECT MIN(id) FROM fashion_categories WHERE is_active = true", Long.class)))
                .param("tags", "古着")
                .param("description", "非公開の写真の確認の説明");
        String body = as(author, request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** 管理者の API で非公開にする（投稿者に通知が届く） */
    private void hide(long postId) throws Exception {
        as(admin, post("/api/admin/moderation/posts/" + postId + "/hide").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"SPAM\"}"))
                .andExpect(status().isOk());
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions as(User user, B request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(user)));
    }

    private String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
