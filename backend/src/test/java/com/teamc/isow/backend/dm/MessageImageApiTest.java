package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * DM の画像の配信（GET /api/conversations/{conversationId}/messages/{messageId}/image。docs/dm.md「画像」）。
 * DM の画像は、会話の当事者2人にだけ配る。{@code <img>} のタグは認証のヘッダーを付けられないため、
 * 画面はこの API から中身を取得し、その場限りの URL（URL.createObjectURL）にして表示する。
 *
 * <ul>
 *   <li>当事者2人には返す（終了した会話・ブロックで終わった会話・相手が利用停止中でも。過去のメッセージは両方から読めるため）</li>
 *   <li>当事者でない人・管理者は 404（存在しないメッセージと同じ）。ログインしていなければ 401</li>
 *   <li>保存先は公開の画像と同じフォルダのまま。/uploads/** からは配らない（ここが唯一の防波堤）</li>
 *   <li>メッセージの応答には、保存先のファイル名を出さない（hasImage だけ）</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessageImageApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.image.local.dir}")
    private Path uploadDir;

    /** 申し込んだ側 */
    private User userA;
    /** 申し込まれた側 */
    private User userB;
    /** 当事者でない人 */
    private User outsider;
    /** 管理者（当事者ではない） */
    private User admin;
    /** A から B への、承認済み（進行中）の会話 */
    private long conversationId;

    @BeforeEach
    void setUp() throws Exception {
        userA = saveUser(0, "dm_a");
        userB = saveUser(1, "dm_b");
        outsider = saveUser(2, "dm_c");
        admin = saveUser(3, "dm_d");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        conversationId = activeConversation(userA, userB);
    }

    @AfterEach
    void tearDown() {
        DmTestSupport.cleanUp(jdbcTemplate);
    }

    /** DB には保存先のキー（ファイル名）だけを持ち、ホスト名入りの URL は持たない（R2 の作業のステップ1） */
    @Test
    void DMの画像はDBにキーだけを保存する() throws Exception {
        long messageId = sendImage(userA, conversationId, png());

        assertThat(jdbcTemplate.queryForObject("SELECT image_key FROM messages WHERE id = ?", String.class, messageId))
                .matches("[0-9a-f]{32}\\.png");
    }

    // ---- 当事者には配る ----

    @Test
    void 当事者2人は_画像の中身を取得でき_ブラウザに残さない() throws Exception {
        byte[] png = png();
        long messageId = sendImage(userA, conversationId, png);
        byte[] stored = Files.readAllBytes(uploadDir.resolve(fileName(messageId)));

        for (User viewer : new User[] {userA, userB}) {
            image(viewer, conversationId, messageId)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/png"))
                    .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(content().bytes(stored));
        }
    }

    @Test
    void 終了した会話の画像も_当事者は取得できる() throws Exception {
        long messageId = sendImage(userA, conversationId, png());
        as(userB, post("/api/conversations/" + conversationId + "/end")).andExpect(status().isOk());

        image(userA, conversationId, messageId).andExpect(status().isOk());
        image(userB, conversationId, messageId).andExpect(status().isOk());
    }

    @Test
    void ブロックで終わった会話の画像も_当事者は取得できる() throws Exception {
        long messageId = sendImage(userA, conversationId, png());
        as(userB, post("/api/users/" + userA.getId() + "/block")).andExpect(status().is2xxSuccessful());

        image(userA, conversationId, messageId).andExpect(status().isOk());
        image(userB, conversationId, messageId).andExpect(status().isOk());
    }

    @Test
    void 送った人が利用停止中でも_相手は取得できる_本人は利用停止のため403() throws Exception {
        long messageId = sendImage(userA, conversationId, png());
        jdbcTemplate.update("UPDATE users SET suspended_at = ?, suspended_until = NULL, suspension_reason = 'HARASSMENT' "
                + "WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusDays(1)), userA.getId());

        image(userB, conversationId, messageId).andExpect(status().isOk());
        image(userA, conversationId, messageId).andExpect(status().isForbidden());
    }

    // ---- 当事者以外には配らない ----

    @Test
    void 当事者でない人と管理者は404_ログインしていなければ401() throws Exception {
        long messageId = sendImage(userA, conversationId, png());

        image(outsider, conversationId, messageId).andExpect(status().isNotFound());
        image(admin, conversationId, messageId).andExpect(status().isNotFound());
        mockMvc.perform(get(imagePath(conversationId, messageId))).andExpect(status().isUnauthorized());
    }

    @Test
    void 当事者でない人への404は_存在しないメッセージの404と同じ形() throws Exception {
        long messageId = sendImage(userA, conversationId, png());

        String notParticipant = image(outsider, conversationId, messageId).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = image(userA, conversationId, 999999999L).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(notParticipant).isEqualTo(missing);
    }

    @Test
    void 別の会話のメッセージを_自分の会話のIDと組み合わせても404() throws Exception {
        long other = activeConversation(outsider, userB);
        long otherMessage = sendImage(outsider, other, png());

        // B は両方の会話の当事者だが、メッセージは指定した会話のものでなければならない
        image(userB, conversationId, otherMessage).andExpect(status().isNotFound());
        image(userA, conversationId, otherMessage).andExpect(status().isNotFound());
    }

    @Test
    void 画像のないメッセージと_ファイルがなくなった画像は404() throws Exception {
        long textOnly = sendText(userA, conversationId, "文字だけ");
        long messageId = sendImage(userA, conversationId, png());
        Files.delete(uploadDir.resolve(fileName(messageId)));

        image(userA, conversationId, textOnly).andExpect(status().isNotFound());
        image(userA, conversationId, messageId).andExpect(status().isNotFound());
    }

    // ---- /uploads からは配らない（唯一の防波堤） ----

    @Test
    void DMの画像は_ファイル名が分かっても_uploadsからは誰も取得できない() throws Exception {
        long messageId = sendImage(userA, conversationId, png());
        String path = "/uploads/" + fileName(messageId);
        // 確かめる意味があるか：ファイルは公開の画像と同じフォルダにある
        assertThat(uploadDir.resolve(fileName(messageId))).exists();

        mockMvc.perform(get(path)).andExpect(status().isNotFound());
        as(userA, get(path)).andExpect(status().isNotFound());
        as(userB, get(path)).andExpect(status().isNotFound());
        as(admin, get(path)).andExpect(status().isNotFound());
    }

    // ---- 応答に保存先を出さない ----

    @Test
    void 送信と一覧の応答は_hasImageだけを返し_保存先のファイル名を出さない() throws Exception {
        String sent = as(userA, multipart("/api/conversations/" + conversationId + "/messages")
                        .file(new MockMultipartFile("image", "a.png", "image/png", png())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasImage").value(true))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long messageId = ((Number) JsonPath.read(sent, "$.id")).longValue();
        String name = fileName(messageId);
        assertThat(sent).doesNotContain(name);
        sendText(userA, conversationId, "文字だけ");

        String listed = as(userB, get("/api/conversations/" + conversationId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[?(@.id == " + messageId + ")].hasImage").value(true))
                .andExpect(jsonPath("$.messages[?(@.body == '文字だけ')].hasImage").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(listed).doesNotContain(name).doesNotContain("imageUrl");
    }

    // ---- 部品 ----

    private User saveUser(int index, String username) {
        return userRepository.save(new User(DmTestSupport.EMAILS[index], "0900000005" + index, "hash", username));
    }

    /** from が to に相談を申し込み、to が承認した会話の ID */
    private long activeConversation(User from, User to) throws Exception {
        String body = as(from, post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":" + to.getId() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.conversationId")).longValue();
        as(to, post("/api/conversations/" + id + "/accept")).andExpect(status().isOk());
        return id;
    }

    private long sendImage(User sender, long id, byte[] png) throws Exception {
        String body = as(sender, multipart("/api/conversations/" + id + "/messages")
                        .file(new MockMultipartFile("image", "a.png", "image/png", png)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long sendText(User sender, long id, String text) throws Exception {
        String body = as(sender, multipart("/api/conversations/" + id + "/messages").param("body", text))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** 保存先のファイル名（DB の URL の最後。応答には出さないため DB から読む） */
    private String fileName(long messageId) {
        String url = jdbcTemplate.queryForObject("SELECT image_key FROM messages WHERE id = ?", String.class, messageId);
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static String imagePath(long conversationId, long messageId) {
        return "/api/conversations/" + conversationId + "/messages/" + messageId + "/image";
    }

    private ResultActions image(User viewer, long conversationId, long messageId) throws Exception {
        return as(viewer, get(imagePath(conversationId, messageId)));
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions as(User user, B request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + DmTestSupport.token(jwtEncoder, user.getId())));
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
