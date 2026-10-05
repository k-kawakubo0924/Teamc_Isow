package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * メッセージの送信・取得（POST・GET /api/conversations/{id}/messages）と、DM一覧（GET /api/conversations）。
 * API がトランザクションを自分で管理するため、テストはロールバックせず、終了時に作ったデータを消す。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessageApiTest {

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
    /** A から B への、承認済み（進行中）の会話。申込時の一言が1通ある */
    private long conversationId;

    @BeforeEach
    void setUp() throws Exception {
        userA = saveUser(0, "dm_a");
        userB = saveUser(1, "dm_b");
        outsider = saveUser(2, "dm_c");
        conversationId = request(userA, userB, "よろしくお願いします");
        mockMvc.perform(post("/api/conversations/" + conversationId + "/accept").header("Authorization", bearer(userB)))
                .andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        DmTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- 送信 ----

    @Test
    void テキストを送信すると_最終メッセージ日時が送信日時になる() throws Exception {
        String body = send(userB, conversationId, "  こちらこそ  ", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.senderId").value(userB.getId()))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.body").value("こちらこそ"))
                .andExpect(jsonPath("$.imageUrl").isEmpty())
                .andExpect(jsonPath("$.sentAt").isNotEmpty())
                .andExpect(jsonPath("$.readAt").isEmpty())
                .andReturn().getResponse().getContentAsString();

        Integer messageId = JsonPath.read(body, "$.id");
        Object sentAt = jdbcTemplate.queryForObject("SELECT sent_at FROM messages WHERE id = ?", Object.class, messageId);
        Object lastMessageAt = jdbcTemplate.queryForObject(
                "SELECT last_message_at FROM conversations WHERE id = ?", Object.class, conversationId);
        assertThat(lastMessageAt).isEqualTo(sentAt);
    }

    @Test
    void 画像だけ_または本文と画像を一緒に送信できる() throws Exception {
        byte[] png = png();

        String imageOnly = send(userA, conversationId, null, new MockMultipartFile("image", "a.png", "image/png", png))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").isEmpty())
                .andExpect(jsonPath("$.imageUrl", startsWith("http://localhost/uploads/")))
                .andReturn().getResponse().getContentAsString();
        send(userA, conversationId, "これはどうですか", new MockMultipartFile("image", "b.png", "image/png", png))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("これはどうですか"))
                .andExpect(jsonPath("$.imageUrl", startsWith("http://localhost/uploads/")));

        String url = JsonPath.read(imageOnly, "$.imageUrl");
        assertThat(Files.readAllBytes(uploadDir.resolve(url.substring(url.lastIndexOf('/') + 1)))).isEqualTo(png);
    }

    @Test
    void 本文も画像もない場合は400() throws Exception {
        send(userA, conversationId, "   ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").value("メッセージを入力するか、画像を選択してください"));
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void 本文が1000文字を超える場合は400() throws Exception {
        send(userA, conversationId, "あ".repeat(1001), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").value("メッセージは1000文字以内で入力してください"));
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void 画像でないファイルは400で_何も保存しない() throws Exception {
        Set<String> filesBefore = storedFiles();

        send(userA, conversationId, "見てください", new MockMultipartFile("image", "a.png", "image/png", "text".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.image").value("JPEG または PNG の画像を選択してください。"));
        assertThat(messageCount()).isEqualTo(1);
        assertThat(storedFiles()).isEqualTo(filesBefore);
    }

    @Test
    void 進行中でない会話には送信できず_画像も保存しない() throws Exception {
        long pending = request(userA, outsider, null);
        Set<String> filesBefore = storedFiles();

        expectInvalidStatus(send(userA, pending, "こんにちは", null), "申請中");
        expectInvalidStatus(
                send(userA, pending, null, new MockMultipartFile("image", "a.png", "image/png", png())), "申請中");

        mockMvc.perform(post("/api/conversations/" + conversationId + "/end").header("Authorization", bearer(userA)))
                .andExpect(status().isOk());
        expectInvalidStatus(send(userB, conversationId, "まだ話せますか", null), "終了");

        assertThat(messageCount()).isEqualTo(1);
        assertThat(storedFiles()).isEqualTo(filesBefore);
    }

    @Test
    void 当事者でない人には_送信も取得も存在しない会話と同じ404() throws Exception {
        String sendNotParticipant = send(outsider, conversationId, "こんにちは", null)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String sendNotExists = send(outsider, 999_999L, "こんにちは", null)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(sendNotParticipant).isEqualTo(sendNotExists).contains("会話が見つかりません。");

        String getNotParticipant = messages(outsider, conversationId, "")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String getNotExists = messages(outsider, 999_999L, "")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(getNotParticipant).isEqualTo(getNotExists).isEqualTo(sendNotParticipant);

        // 当事者でない人が開いても既読にならない
        assertThat(unreadCount()).isEqualTo(1);
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void トークンがない場合は401() throws Exception {
        mockMvc.perform(multipart("/api/conversations/" + conversationId + "/messages").param("body", "こんにちは"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
    }

    // ---- 取得 ----

    @Test
    void 古い順に返し_beforeでさらに古いメッセージを読める() throws Exception {
        for (int i = 1; i <= 5; i++) {
            send(i % 2 == 0 ? userA : userB, conversationId, "メッセージ" + i, null).andExpect(status().isCreated());
        }
        // 申込時の一言 + 5通 = 6通。最新の4通を古い順で
        String latest = messages(userA, conversationId, "?size=4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(4)))
                .andExpect(jsonPath("$.messages[0].mine").value(true))
                .andExpect(jsonPath("$.messages[1].mine").value(false))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(bodies(latest)).containsExactly("メッセージ2", "メッセージ3", "メッセージ4", "メッセージ5");

        Integer oldestId = JsonPath.read(latest, "$.messages[0].id");
        String older = messages(userA, conversationId, "?size=4&before=" + oldestId)
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(bodies(older)).containsExactly("よろしくお願いします", "メッセージ1");
    }

    @Test
    void 取得すると相手から届いた未読メッセージがすべて既読になり_自分のメッセージは変わらない() throws Exception {
        send(userB, conversationId, "Bから1", null).andExpect(status().isCreated());
        send(userB, conversationId, "Bから2", null).andExpect(status().isCreated());
        send(userA, conversationId, "Aから", null).andExpect(status().isCreated());

        // B が開く（size=1 でも、A から届いた未読はすべて既読にする）
        messages(userB, conversationId, "?size=1")
                .andExpect(jsonPath("$.messages[0].body").value("Aから"))
                .andExpect(jsonPath("$.messages[0].readAt").isNotEmpty());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT sender_id, read_at FROM messages ORDER BY id");
        // 申込時の一言（A）・A から → 既読。B から1・2 → A がまだ開いていないので未読
        assertThat(rows.get(0).get("READ_AT")).isNotNull();
        assertThat(rows.get(1).get("READ_AT")).isNull();
        assertThat(rows.get(2).get("READ_AT")).isNull();
        assertThat(rows.get(3).get("READ_AT")).isNotNull();
    }

    @Test
    void 終了した会話も当事者なら読める() throws Exception {
        mockMvc.perform(post("/api/conversations/" + conversationId + "/end").header("Authorization", bearer(userB)))
                .andExpect(status().isOk());

        messages(userA, conversationId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value("よろしくお願いします"));
    }

    // ---- DM一覧 ----

    @Test
    void DM一覧に相手_最新メッセージ_未読件数_状態を返す() throws Exception {
        send(userB, conversationId, "Bから1", null).andExpect(status().isCreated());
        send(userB, conversationId, null, new MockMultipartFile("image", "a.png", "image/png", png()))
                .andExpect(status().isCreated());

        list(userA, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversations", hasSize(1)))
                .andExpect(jsonPath("$.conversations[0].conversationId").value(conversationId))
                .andExpect(jsonPath("$.conversations[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.conversations[0].requestedByMe").value(true))
                .andExpect(jsonPath("$.conversations[0].partner.id").value(userB.getId()))
                .andExpect(jsonPath("$.conversations[0].partner.username").value("dm_b"))
                .andExpect(jsonPath("$.conversations[0].partner.displayName").value("dm_b"))
                .andExpect(jsonPath("$.conversations[0].partner.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.conversations[0].partner.email").doesNotExist())
                // 最新は画像だけのメッセージ
                .andExpect(jsonPath("$.conversations[0].lastMessageBody").isEmpty())
                .andExpect(jsonPath("$.conversations[0].lastMessageHasImage").value(true))
                .andExpect(jsonPath("$.conversations[0].lastMessageAt").isNotEmpty())
                .andExpect(jsonPath("$.conversations[0].unreadCount").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));

        // B から見ると相手は A。申込時の一言はまだ読んでいない
        list(userB, "")
                .andExpect(jsonPath("$.conversations[0].partner.id").value(userA.getId()))
                .andExpect(jsonPath("$.conversations[0].requestedByMe").value(false))
                .andExpect(jsonPath("$.conversations[0].unreadCount").value(1));

        // A が開くと未読が0になる
        messages(userA, conversationId, "").andExpect(status().isOk());
        list(userA, "").andExpect(jsonPath("$.conversations[0].unreadCount").value(0));
    }

    @Test
    void DM一覧は最終メッセージの新しい順で_メッセージのない会話は申込日時で並ぶ() throws Exception {
        User d = saveUser(3, "dm_d");
        User e = saveUser(4, "dm_e");
        long withoutMessage = request(d, userA, null);
        long requestFromE = request(e, userA, "相談です");
        // 最初の会話に新しいメッセージを送り、先頭にする
        send(userB, conversationId, "最新", null).andExpect(status().isCreated());

        assertThat(listedIds(userA, "")).containsExactly(conversationId, requestFromE, withoutMessage);
        list(userA, "")
                .andExpect(jsonPath("$.conversations[2].lastMessageBody").isEmpty())
                .andExpect(jsonPath("$.conversations[2].lastMessageHasImage").value(false))
                .andExpect(jsonPath("$.conversations[2].lastMessageAt").isEmpty())
                .andExpect(jsonPath("$.conversations[2].unreadCount").value(0));
    }

    @Test
    void DM一覧は状態で絞り込め_拒否した会話は出さない() throws Exception {
        User d = saveUser(3, "dm_d");
        User e = saveUser(4, "dm_e");
        User f = saveUser(5, "dm_f");
        long requested = request(d, userA, null);
        long ended = request(e, userA, null);
        mockMvc.perform(post("/api/conversations/" + ended + "/accept").header("Authorization", bearer(userA)));
        mockMvc.perform(post("/api/conversations/" + ended + "/end").header("Authorization", bearer(userA)))
                .andExpect(status().isOk());
        long rejected = request(f, userA, null);
        mockMvc.perform(post("/api/conversations/" + rejected + "/reject").header("Authorization", bearer(userA)))
                .andExpect(status().isOk());

        assertThat(listedIds(userA, "")).containsExactlyInAnyOrder(conversationId, requested, ended);
        assertThat(listedIds(userA, "?status=requested")).containsExactly(requested);
        assertThat(listedIds(userA, "?status=active")).containsExactly(conversationId);
        assertThat(listedIds(userA, "?status=ended")).containsExactly(ended);
        // 拒否された側にも出さない
        assertThat(listedIds(f, "")).isEmpty();
        // 当事者でない会話は出さない
        assertThat(listedIds(outsider, "")).isEmpty();
    }

    @Test
    void DM一覧の絞り込みの値が正しくない場合は400() throws Exception {
        list(userA, "?status=rejected")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("絞り込みの指定が正しくありません"));
    }

    @Test
    void DM一覧はページで区切れる() throws Exception {
        User d = saveUser(3, "dm_d");
        request(d, userA, null);

        list(userA, "?size=1")
                .andExpect(jsonPath("$.conversations", hasSize(1)))
                .andExpect(jsonPath("$.hasNext").value(true));
        list(userA, "?size=1&page=1")
                .andExpect(jsonPath("$.conversations", hasSize(1)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ---- 部品 ----

    private User saveUser(int index, String username) {
        return userRepository.save(new User(DmTestSupport.EMAILS[index], "0900000005" + index, "hash", username));
    }

    /** 相談を申し込み、会話のIDを返す */
    private long request(User from, User to, String message) throws Exception {
        String messagePart = message == null ? "null" : "\"" + message + "\"";
        String body = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", bearer(from))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":" + to.getId() + ",\"message\":" + messagePart + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.conversationId")).longValue();
    }

    private ResultActions send(User sender, long id, String body, MockMultipartFile image) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/conversations/" + id + "/messages");
        if (body != null) {
            request.param("body", body);
        }
        if (image != null) {
            request.file(image);
        }
        return mockMvc.perform(request.header("Authorization", bearer(sender)));
    }

    private ResultActions messages(User viewer, long id, String query) throws Exception {
        return mockMvc.perform(get("/api/conversations/" + id + "/messages" + query)
                .header("Authorization", bearer(viewer)));
    }

    private ResultActions list(User viewer, String query) throws Exception {
        return mockMvc.perform(get("/api/conversations" + query).header("Authorization", bearer(viewer)));
    }

    private List<Long> listedIds(User viewer, String query) throws Exception {
        String body = list(viewer, query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.conversations[*].conversationId");
        return ids.stream().map(Number::longValue).toList();
    }

    private static List<String> bodies(String messageListJson) {
        return JsonPath.read(messageListJson, "$.messages[*].body");
    }

    private static void expectInvalidStatus(ResultActions result, String label) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.message").value("この会話は" + label + "のため、メッセージを送信できません。"));
    }

    private String bearer(User user) {
        return "Bearer " + DmTestSupport.token(jwtEncoder, user.getId());
    }

    private int messageCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM messages", Integer.class);
    }

    private int unreadCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM messages WHERE read_at IS NULL", Integer.class);
    }

    private Set<String> storedFiles() throws IOException {
        if (!Files.exists(uploadDir)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(uploadDir)) {
            return files.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
