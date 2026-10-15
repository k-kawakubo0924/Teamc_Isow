package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 相談ごとの通知オフ（POST・DELETE /api/conversations/{id}/mute。docs/dm.md「通知オフ」）。
 *
 * <ul>
 *   <li>メッセージは届く。知らせないだけ（DM一覧の行の未読の数は出る）</li>
 *   <li>DM のバッジ（未読メッセージの合計）とホームの新着メッセージに数えない。メッセージの通知を作らず、未読にも戻さない</li>
 *   <li>自分の設定で、相手には分からない</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConversationMuteApiTest {

    private static final String EMAIL_PATTERN = "dm-mute-%@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User partner;
    private User other;

    @BeforeEach
    void setUp() {
        me = saveUser("me", "09000000701");
        partner = saveUser("partner", "09000000702");
        other = saveUser("other", "09000000703");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM conversation_user_settings");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PATTERN);
    }

    @Test
    void 通知をオフにすると詳細と一覧に出て_相手には分からず_通知もしない() throws Exception {
        long id = active(me, partner);
        long notificationsBefore = count("SELECT count(*) FROM notifications");

        mute(me, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.muted").value(true))
                .andExpect(jsonPath("$.pinned").value(false));
        mute(me, id).andExpect(status().isOk()).andExpect(jsonPath("$.muted").value(true));

        getAs(me, "/api/conversations/" + id).andExpect(jsonPath("$.muted").value(true));
        getAs(me, "/api/conversations?status=chats").andExpect(jsonPath("$.conversations[0].muted").value(true));
        getAs(partner, "/api/conversations/" + id).andExpect(jsonPath("$.muted").value(false));
        getAs(partner, "/api/conversations?status=chats").andExpect(jsonPath("$.conversations[0].muted").value(false));
        assertThat(count("SELECT count(*) FROM notifications")).isEqualTo(notificationsBefore);
    }

    @Test
    void 通知オフでもメッセージは届き_行の未読は出るが_DMのバッジと新着メッセージには数えない() throws Exception {
        long muted = active(me, partner);
        long normal = active(me, other);
        mute(me, muted).andExpect(status().isOk());

        send(partner, muted, "通知オフの相談へのメッセージ").andExpect(status().isCreated());
        send(other, normal, "通知オンの相談へのメッセージ").andExpect(status().isCreated());

        // 届いている（読める・行の未読の数が出る）
        getAs(me, "/api/conversations?status=chats")
                .andExpect(jsonPath("$.conversations[?(@.conversationId == " + muted + ")].unreadCount").value(1));
        getAs(me, "/api/conversations/" + muted + "/messages")
                .andExpect(jsonPath("$.messages[-1].body").value("通知オフの相談へのメッセージ"));
        // 開いて既読にする前の、別の相談だけが数えられている
        send(partner, muted, "もう1件").andExpect(status().isCreated());
        getAs(me, "/api/conversations/summary").andExpect(jsonPath("$.unreadMessageCount").value(1));
        getAs(me, "/api/conversations?status=chats&unread=true")
                .andExpect(jsonPath("$.conversations.length()").value(1))
                .andExpect(jsonPath("$.conversations[0].conversationId").value(normal));
    }

    @Test
    void 通知オフの相談には_メッセージの通知を作らず_既読にした通知も未読に戻さない() throws Exception {
        long id = active(me, partner);
        send(partner, id, "オフにする前").andExpect(status().isCreated());
        long notificationId = messageNotificationId(me, id);
        mockMvc.perform(post("/api/notifications/" + notificationId + "/read").header("Authorization", bearer(me)))
                .andExpect(status().is2xxSuccessful());
        mute(me, id).andExpect(status().isOk());

        send(partner, id, "オフにした後").andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = 'MESSAGE_RECEIVED'",
                me.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM notifications WHERE id = ? AND read_at IS NULL", notificationId)).isZero();

        // 相手（通知オンのまま）には今までどおり通知する
        send(me, id, "自分から").andExpect(status().isCreated());
        assertThat(count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = 'MESSAGE_RECEIVED'",
                partner.getId())).isEqualTo(1);
    }

    @Test
    void オンに戻すと_次のメッセージから通知とバッジが戻る() throws Exception {
        long id = active(me, partner);
        mute(me, id).andExpect(status().isOk());
        unmute(me, id).andExpect(status().isOk()).andExpect(jsonPath("$.muted").value(false));

        send(partner, id, "オンに戻した後").andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = 'MESSAGE_RECEIVED'",
                me.getId())).isEqualTo(1);
        getAs(me, "/api/conversations/summary").andExpect(jsonPath("$.unreadMessageCount").value(1));
    }

    @Test
    void 通知をオフにできるのは進行中だけ_オンに戻すのはいつでもできる() throws Exception {
        long id = active(me, partner);
        mute(me, id).andExpect(status().isOk());
        operate(me, id, "end").andExpect(status().isOk());

        unmute(me, id).andExpect(status().isOk()).andExpect(jsonPath("$.muted").value(false));
        mute(me, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.message").value("この会話は終了のため、通知をオフにできません。"));
        long requested = request(me, other);
        mute(me, requested).andExpect(status().isConflict());
    }

    @Test
    void 当事者でなければ404_トークンなしは401() throws Exception {
        long id = active(me, partner);

        mute(other, id).andExpect(status().isNotFound());
        unmute(other, id).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/conversations/" + id + "/mute")).andExpect(status().isUnauthorized());
    }

    @Test
    void ピン留めと通知オフは別々に外せて_どちらもなくなったら設定の行を消す() throws Exception {
        long id = active(me, partner);
        mute(me, id).andExpect(status().isOk());
        mockMvc.perform(post("/api/conversations/" + id + "/pin").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.muted").value(true));

        mockMvc.perform(delete("/api/conversations/" + id + "/pin").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.pinned").value(false))
                .andExpect(jsonPath("$.muted").value(true));
        assertThat(count("SELECT count(*) FROM conversation_user_settings")).isEqualTo(1);

        unmute(me, id).andExpect(jsonPath("$.muted").value(false));
        assertThat(count("SELECT count(*) FROM conversation_user_settings")).isZero();
    }

    // ---- 部品 ----

    private User saveUser(String name, String phone) {
        return userRepository.save(new User("dm-mute-" + name + "@example.com", phone, "hash", "dmmute_" + name));
    }

    private ResultActions mute(User user, long id) throws Exception {
        return mockMvc.perform(post("/api/conversations/" + id + "/mute").header("Authorization", bearer(user)));
    }

    private ResultActions unmute(User user, long id) throws Exception {
        return mockMvc.perform(delete("/api/conversations/" + id + "/mute").header("Authorization", bearer(user)));
    }

    private ResultActions send(User user, long id, String body) throws Exception {
        return mockMvc.perform(multipart("/api/conversations/" + id + "/messages").param("body", body)
                .header("Authorization", bearer(user)));
    }

    private ResultActions getAs(User user, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(user)));
    }

    private long request(User from, User to) throws Exception {
        String body = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", bearer(from))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":" + to.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"conversationId\":(\\d+).*", "$1"));
    }

    private long active(User from, User to) throws Exception {
        long id = request(from, to);
        operate(to, id, "accept").andExpect(status().isOk());
        return id;
    }

    private ResultActions operate(User user, long id, String operation) throws Exception {
        return mockMvc.perform(post("/api/conversations/" + id + "/" + operation).header("Authorization", bearer(user)));
    }

    private long messageNotificationId(User recipient, long conversationId) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM notifications WHERE recipient_id = ? AND conversation_id = ? "
                + "AND type = 'MESSAGE_RECEIVED'", Long.class, recipient.getId(), conversationId);
        return id == null ? 0 : id;
    }

    private String bearer(User user) {
        return "Bearer " + DmTestSupport.token(jwtEncoder, user.getId());
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
