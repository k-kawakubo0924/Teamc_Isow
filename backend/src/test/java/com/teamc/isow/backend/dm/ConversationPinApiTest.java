package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 * 相談のピン留め（POST・DELETE /api/conversations/{id}/pin。docs/dm.md「ピン留め」）。
 * ピン留めは1人ごとの設定で、相手には分からない（相手への応答に含めず、通知もしない）。上限は10件
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConversationPinApiTest {

    private static final String EMAIL_PATTERN = "dm-pin-%@example.com";

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
    private User outsider;

    @BeforeEach
    void setUp() {
        me = saveUser("me");
        partner = saveUser("partner");
        outsider = saveUser("outsider");
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
    void ピン留めすると一覧_詳細_件数に出て_もう一度押しても1件のまま() throws Exception {
        long id = active(me, partner);

        pin(me, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(id))
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.muted").value(false));
        pin(me, id).andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(true));

        getAs(me, "/api/conversations/" + id).andExpect(jsonPath("$.pinned").value(true));
        getAs(me, "/api/conversations?status=chats").andExpect(jsonPath("$.conversations[0].pinned").value(true));
        getAs(me, "/api/conversations/summary").andExpect(jsonPath("$.pinnedCount").value(1));
    }

    @Test
    void 相手には分からない_相手の応答はピン留めなしのままで_通知も作らない() throws Exception {
        long id = active(me, partner);
        long notificationsBefore = count("SELECT count(*) FROM notifications");

        pin(me, id).andExpect(status().isOk());

        getAs(partner, "/api/conversations/" + id).andExpect(jsonPath("$.pinned").value(false));
        getAs(partner, "/api/conversations?status=chats").andExpect(jsonPath("$.conversations[0].pinned").value(false));
        getAs(partner, "/api/conversations/summary").andExpect(jsonPath("$.pinnedCount").value(0));
        getAs(partner, "/api/conversations?status=chats&pinned=true").andExpect(jsonPath("$.conversations").isEmpty());
        assertThat(count("SELECT count(*) FROM notifications")).isEqualTo(notificationsBefore);
    }

    @Test
    void 外すと一覧と件数から消え_もう一度外してもよい() throws Exception {
        long id = active(me, partner);
        pin(me, id).andExpect(status().isOk());

        unpin(me, id).andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(false));
        unpin(me, id).andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(false));

        getAs(me, "/api/conversations/summary").andExpect(jsonPath("$.pinnedCount").value(0));
        getAs(me, "/api/conversations?status=chats&pinned=true").andExpect(jsonPath("$.conversations").isEmpty());
    }

    @Test
    void 終了した相談もピン留めでき_申請中と拒否はできない() throws Exception {
        long ended = active(me, partner);
        operate(me, ended, "end").andExpect(status().isOk());
        pin(me, ended).andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(true));

        long requested = request(me, outsider);
        pin(me, requested).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.message").value("この会話は申請中のため、ピン留めできません。"));
        operate(outsider, requested, "reject").andExpect(status().isOk());
        pin(me, requested).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("INVALID_STATUS"));
    }

    @Test
    void 当事者でなければ404_トークンなしは401() throws Exception {
        long id = active(me, partner);

        pin(outsider, id).andExpect(status().isNotFound());
        unpin(outsider, id).andExpect(status().isNotFound());
        pin(me, 999_999L).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/conversations/" + id + "/pin")).andExpect(status().isUnauthorized());
        assertThat(count("SELECT count(*) FROM conversation_user_settings")).isZero();
    }

    @Test
    void ピン留めは10件まで_11件目は409で_外せばまたできる() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            ids.add(active(me, saveUser("p" + i)));
        }
        for (int i = 0; i < 10; i++) {
            pin(me, ids.get(i)).andExpect(status().isOk());
        }

        pin(me, ids.get(10)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("PIN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value("ピン留めできるのは10件までです。"));
        // すでにピン留めしたものをもう一度押すのは、上限でも成功する
        pin(me, ids.get(0)).andExpect(status().isOk());

        unpin(me, ids.get(0)).andExpect(status().isOk());
        pin(me, ids.get(10)).andExpect(status().isOk());
        getAs(me, "/api/conversations/summary").andExpect(jsonPath("$.pinnedCount").value(10));
    }

    @Test
    void ピン留めの一覧は_ピン留めしたものだけを_DM一覧と同じく最後のメッセージの新しい順に出す() throws Exception {
        long older = active(me, partner);
        long notPinned = active(me, outsider);
        long newer = active(me, saveUser("other"));
        setLastMessageAt(older, LocalDateTime.now().minusDays(2));
        setLastMessageAt(notPinned, LocalDateTime.now().minusDays(1));
        setLastMessageAt(newer, LocalDateTime.now().minusHours(1));
        pin(me, older).andExpect(status().isOk());
        pin(me, newer).andExpect(status().isOk());

        getAs(me, "/api/conversations?status=chats&pinned=true")
                .andExpect(jsonPath("$.conversations[*].conversationId", contains((int) newer, (int) older)));
    }

    // ---- 部品 ----

    private User saveUser(String name) {
        return userRepository.save(new User("dm-pin-" + name + "@example.com",
                "090" + String.format("%08d", Math.abs(name.hashCode()) % 100_000_000), "hash", "dmpin_" + name));
    }

    private ResultActions pin(User user, long id) throws Exception {
        return mockMvc.perform(post("/api/conversations/" + id + "/pin").header("Authorization", bearer(user)));
    }

    private ResultActions unpin(User user, long id) throws Exception {
        return mockMvc.perform(delete("/api/conversations/" + id + "/pin").header("Authorization", bearer(user)));
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

    private void setLastMessageAt(long id, LocalDateTime at) {
        jdbcTemplate.update("UPDATE conversations SET last_message_at = ? WHERE id = ?", Timestamp.valueOf(at), id);
    }

    private String bearer(User user) {
        return "Bearer " + DmTestSupport.token(jwtEncoder, user.getId());
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
