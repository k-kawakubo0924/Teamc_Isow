package com.teamc.isow.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * いいね・フォロー・相談・メッセージの API を呼んだときに、通知が作られることの確認（docs/notification.md）。
 * 通知はそれぞれの処理のコミット後に作られるため、API の応答が返った時点で DB に入っている。
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationCreationTest {

    private static final String[] EMAILS = {
        "notif-a@example.com", "notif-b@example.com"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    private User a;
    private User b;

    @BeforeEach
    void setUp() {
        a = userRepository.save(new User(EMAILS[0], "09000000080", "hash", "notif_a"));
        b = userRepository.save(new User(EMAILS[1], "09000000081", "hash", "notif_b"));
    }

    @AfterEach
    void tearDown() {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", (Object[]) EMAILS);
    }

    // ---- いいね ----

    @Test
    void いいねされると投稿者に通知が届く() throws Exception {
        Post post = savePost(a);

        like(b, post).andExpect(status().isOk());

        assertThat(notifications()).containsExactly(row("LIKED", a, b));
        assertThat(jdbcTemplate.queryForObject("SELECT post_id FROM notifications", Long.class)).isEqualTo(post.getId());
    }

    @Test
    void 同じ人が同じ投稿に再度いいねしても_通知は増えない() throws Exception {
        Post post = savePost(a);
        like(b, post);
        // すでにいいね済み（連打）
        like(b, post);
        // 取り消してから再度いいね
        mockMvc.perform(delete("/api/posts/" + post.getId() + "/like").header("Authorization", bearer(b)));
        like(b, post);

        assertThat(notifications()).containsExactly(row("LIKED", a, b));
    }

    @Test
    void 自分の投稿に自分でいいねしても通知は作らない() throws Exception {
        like(a, savePost(a)).andExpect(status().isOk());

        assertThat(notifications()).isEmpty();
    }

    @Test
    void 自分へのフォローと相談の申込はできず_通知も作られない() throws Exception {
        follow(a, a).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/conversations").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientId\":" + a.getId() + "}"))
                .andExpect(status().isBadRequest());

        assertThat(notifications()).isEmpty();
    }

    @Test
    void 自分が送ったメッセージは自分には通知されない() throws Exception {
        long conversationId = startConversation(b, a);
        jdbcTemplate.update("DELETE FROM notifications");

        sendMessage(b, conversationId, "送信");

        // 送った b には届かず、相手の a にだけ届く
        assertThat(notifications()).containsExactly(row("MESSAGE_RECEIVED", a, b));
    }

    // ---- フォロー ----

    @Test
    void フォローされると通知が届く() throws Exception {
        follow(b, a).andExpect(status().isOk());

        assertThat(notifications()).containsExactly(row("FOLLOWED", a, b));
    }

    @Test
    void 解除から5分以内の再フォローでは通知を作らず_5分を過ぎた再フォローでは作る() throws Exception {
        follow(b, a);
        unfollow(b, a);
        follow(b, a);
        assertThat(notifications()).containsExactly(row("FOLLOWED", a, b));

        unfollow(b, a);
        jdbcTemplate.update("UPDATE follows SET unfollowed_at = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)));
        follow(b, a);

        assertThat(notifications()).containsExactly(row("FOLLOWED", a, b), row("FOLLOWED", a, b));
    }

    // ---- 相談 ----

    @Test
    void 相談が届くと申し込まれた人に_承認されると申し込んだ人に通知が届く() throws Exception {
        long conversationId = requestConsultation(b, a, "よろしくお願いします");
        assertThat(notifications()).containsExactly(row("CONSULTATION_REQUESTED", a, b));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/accept").header("Authorization", bearer(a)))
                .andExpect(status().isOk());

        assertThat(notifications())
                .containsExactly(row("CONSULTATION_REQUESTED", a, b), row("CONSULTATION_APPROVED", b, a));
        // 申込の一言メッセージは、メッセージの通知にはしない
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE conversation_id = ?", Integer.class, conversationId))
                .isEqualTo(2);
    }

    @Test
    void 相談が拒否されると申し込んだ人に通知が届く() throws Exception {
        long conversationId = requestConsultation(b, a, null);

        mockMvc.perform(post("/api/conversations/" + conversationId + "/reject").header("Authorization", bearer(a)))
                .andExpect(status().isOk());

        assertThat(notifications())
                .containsExactly(row("CONSULTATION_REQUESTED", a, b), row("CONSULTATION_REJECTED", b, a));
    }

    // ---- メッセージ ----

    @Test
    void メッセージの通知は会話ごと_受け取る人ごとに1件にまとまる() throws Exception {
        long conversationId = startConversation(b, a);

        sendMessage(b, conversationId, "1通目").andExpect(status().isCreated());
        sendMessage(b, conversationId, "2通目");
        sendMessage(a, conversationId, "返信");

        assertThat(messageNotifications(conversationId)).containsExactly(row("MESSAGE_RECEIVED", a, b),
                row("MESSAGE_RECEIVED", b, a));
    }

    @Test
    void 既読にした後で同じ会話にメッセージが届くと_日時を更新して未読に戻る() throws Exception {
        long conversationId = startConversation(b, a);
        sendMessage(b, conversationId, "1通目");
        Long notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE type = 'MESSAGE_RECEIVED'", Long.class);
        // 通知一覧の API で既読にする（経過がわかるよう、通知日時は過去にしておく）
        LocalDateTime past = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE notifications SET notified_at = ? WHERE id = ?", Timestamp.valueOf(past),
                notificationId);
        mockMvc.perform(post("/api/notifications/" + notificationId + "/read").header("Authorization", bearer(a)))
                .andExpect(status().isNoContent());

        sendMessage(b, conversationId, "2通目");

        Map<String, Object> notification = jdbcTemplate.queryForMap(
                "SELECT id, read_at, notified_at FROM notifications WHERE type = 'MESSAGE_RECEIVED'");
        assertThat(notification.get("id")).isEqualTo(notificationId);
        assertThat(notification.get("read_at")).isNull();
        assertThat(((Timestamp) notification.get("notified_at")).toLocalDateTime()).isAfter(past);
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(a)))
                .andExpect(jsonPath("$.notifications[0].id").value(notificationId))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
    }

    @Test
    void 実際にメッセージが届いても_ベルの未読件数には数えない() throws Exception {
        long conversationId = startConversation(b, a);
        follow(b, a);

        sendMessage(b, conversationId, "1通目");
        sendMessage(b, conversationId, "2通目");

        // a に届いた未読：相談の届いた通知（承認済みでも通知は未読）・フォロー・メッセージ。メッセージだけを除いて数える
        mockMvc.perform(get("/api/notifications/summary").header("Authorization", bearer(a)))
                .andExpect(jsonPath("$.unreadCount").value(2));
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(a)))
                .andExpect(jsonPath("$.notifications[0].type").value("MESSAGE_RECEIVED"))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
    }

    // ---- 通知の作成に失敗した場合 ----

    @Test
    void 通知の作成に失敗しても_いいね_フォロー_メッセージ送信は成功する() throws Exception {
        Post post = savePost(a);
        long conversationId = startConversation(b, a);
        jdbcTemplate.update("DELETE FROM notifications");
        doThrow(new IllegalStateException("通知の保存に失敗（テスト）")).when(notificationRepository).save(any());
        doThrow(new IllegalStateException("通知の検索に失敗（テスト）"))
                .when(notificationRepository).findMessageNotification(any(), any());

        like(b, post).andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(true));
        follow(b, a).andExpect(status().isOk()).andExpect(jsonPath("$.following").value(true));
        sendMessage(b, conversationId, "届く").andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM likes", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM follows WHERE active = true", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM messages WHERE body = '届く'", Integer.class)).isEqualTo(1);
        assertThat(notifications()).isEmpty();
    }

    @Test
    void いいねやフォローが失敗したときは通知を作らない() throws Exception {
        // 存在しない投稿へのいいね・存在しないユーザーへのフォロー
        mockMvc.perform(post("/api/posts/999999/like").header("Authorization", bearer(b)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users/999999/follow").header("Authorization", bearer(b)))
                .andExpect(status().isNotFound());

        assertThat(notifications()).isEmpty();
    }

    // ---- 部品 ----

    /** 通知を「種類:受け取る人->した人」の形で、作った順に返す */
    private List<String> notifications() {
        return jdbcTemplate.queryForList(
                "SELECT type || ':' || recipient_id || '->' || actor_id FROM notifications ORDER BY id", String.class);
    }

    private List<String> messageNotifications(long conversationId) {
        return jdbcTemplate.queryForList("SELECT type || ':' || recipient_id || '->' || actor_id FROM notifications "
                + "WHERE type = 'MESSAGE_RECEIVED' AND conversation_id = ? ORDER BY id", String.class, conversationId);
    }

    private static String row(String type, User recipient, User actor) {
        return type + ":" + recipient.getId() + "->" + actor.getId();
    }

    private Post savePost(User author) {
        return postRepository.save(new Post(author, "題名",
                fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0), null, "説明", null,
                List.of("http://localhost/uploads/notification.jpg"),
                List.of(tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0))));
    }

    private org.springframework.test.web.servlet.ResultActions like(User user, Post post) throws Exception {
        return mockMvc.perform(post("/api/posts/" + post.getId() + "/like").header("Authorization", bearer(user)));
    }

    private org.springframework.test.web.servlet.ResultActions follow(User follower, User followee) throws Exception {
        return mockMvc.perform(post("/api/users/" + followee.getId() + "/follow").header("Authorization", bearer(follower)));
    }

    private void unfollow(User follower, User followee) throws Exception {
        mockMvc.perform(delete("/api/users/" + followee.getId() + "/follow").header("Authorization", bearer(follower)))
                .andExpect(status().isOk());
    }

    /** 相談を申し込み、会話の ID を返す */
    private long requestConsultation(User requester, User recipient, String message) throws Exception {
        String body = message == null
                ? "{\"recipientId\":" + recipient.getId() + "}"
                : "{\"recipientId\":" + recipient.getId() + ",\"message\":\"" + message + "\"}";
        String response = mockMvc.perform(post("/api/conversations").header("Authorization", bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.conversationId")).longValue();
    }

    /** 相談を申し込んで承認し、メッセージを送れる会話の ID を返す */
    private long startConversation(User requester, User recipient) throws Exception {
        long conversationId = requestConsultation(requester, recipient, null);
        mockMvc.perform(post("/api/conversations/" + conversationId + "/accept").header("Authorization", bearer(recipient)))
                .andExpect(status().isOk());
        return conversationId;
    }

    private org.springframework.test.web.servlet.ResultActions sendMessage(User sender, long conversationId, String body)
            throws Exception {
        return mockMvc.perform(multipart("/api/conversations/" + conversationId + "/messages")
                .param("body", body).header("Authorization", bearer(sender)));
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
}
