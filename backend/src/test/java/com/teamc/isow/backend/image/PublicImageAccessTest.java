package com.teamc.isow.backend.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * 保存した画像の配信（/uploads/**）の規則（docs/admin.md「6. 措置」の「非公開にした投稿の写真」）。
 * 規則は「アプリで見えないものは、URL でも取れない」。{@code <img>} のタグは認証のヘッダーを付けられず、
 * 誰が見ているかは分からないため、「誰にも見えない」ものを止める。
 *
 * <ul>
 *   <li>配るのは、公開されてよい画像（投稿の写真・プロフィール画像）として参照されているファイルだけ</li>
 *   <li>DM の画像は配らない（当事者2人にだけ、認証付きの API から配る。MessageImageApiTest）</li>
 *   <li>非公開にした投稿・利用停止中の投稿者の投稿の写真は、誰にも配らない（投稿者本人にも）</li>
 *   <li>利用停止中のユーザーのプロフィール画像は配る（DM の相手のアプリにアイコンが出ているため）</li>
 *   <li>止めた写真の 404 は、存在しないファイルの 404 と同じ形（非公開になったことを URL から探れない）</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicImageAccessTest {

    private static final String EMAIL_PATTERN = "img-%@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ImageStorage imageStorage;

    @Value("${app.image.local.dir}")
    private Path uploadDir;

    /** 投稿・プロフィール画像・DM を送る人（措置を受ける人） */
    private User author;
    /** ほかの利用者（DM の相手） */
    private User viewer;
    /** 管理者 */
    private User admin;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("img-author@example.com", "09000000320", "hash", "img_author"));
        viewer = userRepository.save(new User("img-viewer@example.com", "09000000321", "hash", "img_viewer"));
        admin = userRepository.save(new User("img-admin@example.com", "09000000322", "hash", "img_admin"));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversation_user_settings");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM tags WHERE is_official = false");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PATTERN);
    }

    // ---- 配るもの ----

    @Test
    void 投稿の写真とプロフィール画像は_ログインなしで取得でき_ブラウザに残さない() throws Exception {
        List<String> photos = postPhotos(createPost(author, 2));
        String profile = setProfileImage(author);

        for (String key : List.of(photos.get(0), photos.get(1), profile)) {
            mockMvc.perform(get(path(key)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/png"))
                    .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
        }
    }

    @Test
    void DMの画像は_参照されていても配らない_当事者にだけ認証付きのAPIから配るため() throws Exception {
        String dm = sendImage(author, activeConversation(viewer, author));

        mockMvc.perform(get(path(dm))).andExpect(status().isNotFound());
        as(author, get(path(dm))).andExpect(status().isNotFound());
        as(viewer, get(path(dm))).andExpect(status().isNotFound());
    }

    // ---- 投稿の非公開 ----

    @Test
    void 非公開にした投稿の写真は_誰からも404_投稿者本人と管理者も() throws Exception {
        long postId = createPost(author, 2);
        List<String> photos = postPhotos(postId);
        hide(postId);

        for (String key : photos) {
            mockMvc.perform(get(path(key))).andExpect(status().isNotFound());
            as(author, get(path(key))).andExpect(status().isNotFound());
            as(viewer, get(path(key))).andExpect(status().isNotFound());
            as(admin, get(path(key))).andExpect(status().isNotFound());
            // ファイルは消さずに残す（公開に戻すため）
            assertThat(uploadDir.resolve(key)).exists();
        }
    }

    @Test
    void 公開に戻すと_同じURLでまた取得できる() throws Exception {
        long postId = createPost(author, 1);
        String photo = postPhotos(postId).get(0);
        hide(postId);
        mockMvc.perform(get(path(photo))).andExpect(status().isNotFound());

        jdbcTemplate.update("UPDATE posts SET hidden_at = NULL, hidden_reason = NULL WHERE id = ?", postId);

        mockMvc.perform(get(path(photo))).andExpect(status().isOk());
    }

    @Test
    void 非公開にしても_ほかの投稿の写真とプロフィール画像は取得できる() throws Exception {
        long hidden = createPost(author, 1);
        String other = postPhotos(createPost(author, 1)).get(0);
        String profile = setProfileImage(author);
        hide(hidden);

        mockMvc.perform(get(path(other))).andExpect(status().isOk());
        mockMvc.perform(get(path(profile))).andExpect(status().isOk());
    }

    @Test
    void 止めた写真の404は_存在しないファイルの404と同じ形() throws Exception {
        long postId = createPost(author, 1);
        String photo = postPhotos(postId).get(0);
        hide(postId);

        MvcResult hiddenResult = mockMvc.perform(get(path(photo))).andExpect(status().isNotFound()).andReturn();
        MvcResult missingResult = mockMvc.perform(get("/uploads/0123456789abcdef0123456789abcdef.png"))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(hiddenResult.getResponse().getContentAsString())
                .isEqualTo(missingResult.getResponse().getContentAsString());
        assertThat(hiddenResult.getResponse().getContentType()).isEqualTo(missingResult.getResponse().getContentType());
    }

    // ---- 利用停止 ----

    @Test
    void 利用停止中の投稿者の投稿の写真は404_期限を過ぎれば取得できる() throws Exception {
        String photo = postPhotos(createPost(author, 1)).get(0);

        suspend(author, null);
        mockMvc.perform(get(path(photo))).andExpect(status().isNotFound());

        suspend(author, LocalDateTime.now().plusDays(1));
        mockMvc.perform(get(path(photo))).andExpect(status().isNotFound());

        suspend(author, LocalDateTime.now().minusMinutes(1));
        mockMvc.perform(get(path(photo))).andExpect(status().isOk());
    }

    @Test
    void 利用停止を解除すると_投稿の写真はまた取得できる() throws Exception {
        String photo = postPhotos(createPost(author, 1)).get(0);
        suspend(author, null);
        mockMvc.perform(get(path(photo))).andExpect(status().isNotFound());

        jdbcTemplate.update("UPDATE users SET suspended_at = NULL, suspended_until = NULL, suspension_reason = NULL "
                + "WHERE id = ?", author.getId());

        mockMvc.perform(get(path(photo))).andExpect(status().isOk());
    }

    @Test
    void 利用停止中でも_プロフィール画像は取得できる_DMの相手のアプリにアイコンが出ているため() throws Exception {
        String profile = setProfileImage(author);
        suspend(author, null);

        mockMvc.perform(get(path(profile))).andExpect(status().isOk());
    }

    // ---- 参照されていないファイル ----

    @Test
    void どこからも参照されていないファイルは_保存先にあっても404() throws Exception {
        String stored = imageStorage.store(randomName(), png(), "image/png");
        assertThat(uploadDir.resolve(stored)).exists();

        mockMvc.perform(get(path(stored))).andExpect(status().isNotFound());
    }

    @Test
    void 保存するときの形でない名前のファイルは_保存先にあっても404() throws Exception {
        Files.createDirectories(uploadDir);
        Files.write(uploadDir.resolve("notes.txt"), "メモ".getBytes());
        // 参照されていても、形の違う名前は配らない（URL の一部だけが一致して配ってしまうことを防ぐ）
        jdbcTemplate.update("UPDATE users SET profile_image_key = ? WHERE id = ?",
                "http://localhost/uploads/notes.txt", author.getId());

        mockMvc.perform(get("/uploads/notes.txt")).andExpect(status().isNotFound());
        // ../ を含む URL は、ここまで来る前に Spring Security が 400 で断る
        mockMvc.perform(get("/uploads/../uploads/notes.txt")).andExpect(status().is4xxClientError());
    }

    // ---- 部品 ----

    private long createPost(User user, int photos) throws Exception {
        var request = multipart("/api/posts");
        for (int i = 0; i < photos; i++) {
            request.file(new MockMultipartFile("images", i + ".png", "image/png", png()));
        }
        request.param("title", "配信の確認")
                .param("fashionCategoryId", String.valueOf(jdbcTemplate.queryForObject(
                        "SELECT MIN(id) FROM fashion_categories WHERE is_active = true", Long.class)))
                .param("tags", "古着")
                .param("description", "配信の確認の説明");
        String body = as(user, request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private List<String> postPhotos(long postId) {
        return jdbcTemplate.queryForList(
                "SELECT image_key FROM post_images WHERE post_id = ? ORDER BY sort_order", String.class, postId);
    }

    private String setProfileImage(User user) throws Exception {
        as(user, multipart("/api/users/me/profile-image")
                .file(new MockMultipartFile("image", "p.png", "image/png", png())))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT profile_image_key FROM users WHERE id = ?", String.class, user.getId());
    }

    /** from が to に相談を申し込み、to が承認した会話の ID */
    private long activeConversation(User from, User to) throws Exception {
        String body = as(from, post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\": " + to.getId() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.conversationId")).longValue();
        as(to, post("/api/conversations/" + id + "/accept")).andExpect(status().isOk());
        return id;
    }

    /** 画像付きのメッセージを送り、その画像のキーを返す（応答には出さないため DB から読む） */
    private String sendImage(User sender, long conversationId) throws Exception {
        String body = as(sender, multipart("/api/conversations/" + conversationId + "/messages")
                        .file(new MockMultipartFile("image", "m.png", "image/png", png())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return jdbcTemplate.queryForObject("SELECT image_key FROM messages WHERE id = ?", String.class,
                ((Number) JsonPath.read(body, "$.id")).longValue());
    }

    /** 投稿を非公開にした状態にする（管理者の API を通さず、DB に直接書く） */
    private void hide(long postId) {
        jdbcTemplate.update("UPDATE posts SET hidden_at = ?, hidden_reason = 'SPAM' WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), postId);
    }

    /** 利用停止にした状態にする（until が null なら無期限。DB に直接書く） */
    private void suspend(User user, LocalDateTime until) {
        jdbcTemplate.update("UPDATE users SET suspended_at = ?, suspended_until = ?, suspension_reason = 'HARASSMENT' "
                        + "WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                until == null ? null : Timestamp.valueOf(until), user.getId());
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

    private static String path(String key) {
        return "/uploads/" + key;
    }

    /** 保存するときと同じ形の名前（乱数 + 拡張子） */
    private static String randomName() {
        return UUID.randomUUID().toString().replace("-", "") + ".png";
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
