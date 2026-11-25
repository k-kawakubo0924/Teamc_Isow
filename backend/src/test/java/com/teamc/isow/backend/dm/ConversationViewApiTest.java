package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
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
 * DM一覧の画面で使う API の確認。会話1件（GET /api/conversations/{id}）、DM の件数（GET /api/conversations/summary）、
 * DM一覧の検索（q）と、画面用の絞り込み（chats・received・sent）。
 * API がトランザクションを自分で管理するため、テストはロールバックせず、終了時に作ったデータを消す。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConversationViewApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User haru;
    private User ren;
    private User mio;
    private User sora;
    private User outsider;

    @BeforeEach
    void setUp() {
        me = saveUser(0, "dm_me", null);
        haru = saveUser(1, "haru_st", "はる");
        ren = saveUser(2, "ren_027", "Ren");
        mio = saveUser(3, "mio_f", null);
        sora = saveUser(4, "sora_k", null);
        outsider = saveUser(5, "dm_out", null);
    }

    @AfterEach
    void tearDown() {
        DmTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- 会話1件 ----

    @Test
    void 会話1件は相手と状態を返す() throws Exception {
        long id = request(me, haru, "よろしくお願いします");

        get(me, "/api/conversations/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(id))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requestedByMe").value(true))
                .andExpect(jsonPath("$.partner.id").value(haru.getId()))
                .andExpect(jsonPath("$.partner.username").value("haru_st"))
                .andExpect(jsonPath("$.partner.displayName").value("はる"))
                .andExpect(jsonPath("$.partner.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.partner.email").doesNotExist())
                .andExpect(jsonPath("$.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.respondedAt").isEmpty())
                .andExpect(jsonPath("$.endedAt").isEmpty());

        accept(haru, id);
        get(haru, "/api/conversations/" + id)
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.requestedByMe").value(false))
                .andExpect(jsonPath("$.partner.id").value(me.getId()))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());
    }

    @Test
    void 会話1件は当事者でなければ存在しない会話と同じ404() throws Exception {
        long id = request(me, haru, null);

        String notParticipant = get(outsider, "/api/conversations/" + id)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String notExists = get(outsider, "/api/conversations/999999")
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(notParticipant).isEqualTo(notExists).contains("会話が見つかりません。");
    }

    @Test
    void トークンがない場合は401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/conversations/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/conversations/summary"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 画面用の絞り込み ----

    @Test
    void chatsは進行中と終了_receivedは受け取った申請_sentは送った申請() throws Exception {
        long active = request(haru, me, null);
        accept(me, active);
        long ended = request(me, ren, null);
        accept(ren, ended);
        end(me, ended);
        long received = request(mio, me, "相談させてください");
        long sent = request(me, sora, null);

        assertThat(listedIds(me, "?status=chats")).containsExactlyInAnyOrder(active, ended);
        assertThat(listedIds(me, "?status=received")).containsExactly(received);
        assertThat(listedIds(me, "?status=sent")).containsExactly(sent);
        // 相手から見ると、受け取った・送ったが逆になる
        assertThat(listedIds(mio, "?status=sent")).containsExactly(received);
        assertThat(listedIds(sora, "?status=received")).containsExactly(sent);

        get(me, "/api/conversations?status=received")
                .andExpect(jsonPath("$.conversations[0].requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.conversations[0].lastMessageBody").value("相談させてください"));
    }

    @Test
    void unreadを指定すると相手から届いた未読がある会話だけを返す() throws Exception {
        long unread = request(haru, me, null);
        accept(me, unread);
        send(haru, unread, "未読のメッセージ");
        long read = request(ren, me, null);
        accept(me, read);
        send(ren, read, "読んだメッセージ");
        get(me, "/api/conversations/" + read + "/messages").andExpect(status().isOk());
        // 自分が送っただけの会話（相手からの未読はない）
        long mineOnly = request(me, mio, null);
        accept(mio, mineOnly);
        send(me, mineOnly, "自分のメッセージ");

        assertThat(listedIds(me, "?status=chats&unread=true")).containsExactly(unread);
        assertThat(listedIds(me, "?status=chats")).containsExactlyInAnyOrder(unread, read, mineOnly);
    }

    @Test
    void unreadでは相手から届いた最新の未読を返し_自分が後から送ったメッセージは出さない() throws Exception {
        long id = request(haru, me, null);
        accept(me, id);
        send(haru, id, "1通目");
        send(haru, id, "2通目");
        // 自分が後から送っても、新着として出すのは相手の「2通目」
        send(me, id, "自分の返信");

        get(me, "/api/conversations?status=chats&unread=true")
                .andExpect(jsonPath("$.conversations[0].lastMessageBody").value("自分の返信"))
                .andExpect(jsonPath("$.conversations[0].latestUnread.body").value("2通目"))
                .andExpect(jsonPath("$.conversations[0].latestUnread.hasImage").value(false))
                .andExpect(jsonPath("$.conversations[0].latestUnread.sentAt").isNotEmpty())
                .andExpect(jsonPath("$.conversations[0].unreadCount").value(2));
        // unread を指定しない一覧では入れない
        get(me, "/api/conversations?status=chats")
                .andExpect(jsonPath("$.conversations[0].latestUnread").isEmpty());
    }

    // ---- 検索 ----

    @Test
    void 相手のユーザー名か表示名の一部で絞り込める_大文字小文字は区別しない() throws Exception {
        long withHaru = request(haru, me, null);
        long withRen = request(ren, me, null);
        request(mio, me, null);

        assertThat(searchedIds(me, "HARU")).containsExactly(withHaru);
        // 表示名（はる）でも探せる
        assertThat(searchedIds(me, "は")).containsExactly(withHaru);
        assertThat(searchedIds(me, "ren")).containsExactly(withRen);
        // 自分のユーザー名では一致しない（相手だけを対象にする）
        assertThat(searchedIds(me, "dm_me")).isEmpty();
        // % や _ は文字として扱う（_ は ren_027・haru_st・mio_f のすべてに含まれる）
        assertThat(searchedIds(me, "%")).isEmpty();
        assertThat(searchedIds(me, "_0")).containsExactly(withRen);
        assertThat(searchedIds(me, "_")).hasSize(3);
    }

    @Test
    void 検索語が50文字を超える場合は400() throws Exception {
        get(me, "/api/conversations?q=" + "a".repeat(51))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").value("検索する文字は50文字以内で入力してください"));
    }

    // ---- DM の件数 ----

    @Test
    void 件数は_やり取り中の未読メッセージ_受け取った申請_送った申請を返す() throws Exception {
        long active = request(haru, me, "申請の一言");
        accept(me, active);
        send(haru, active, "1通目");
        send(haru, active, "2通目");
        send(me, active, "自分のメッセージは数えない");
        long ended = request(ren, me, null);
        accept(me, ended);
        send(ren, ended, "終了前のメッセージ");
        end(ren, ended);
        // 受け取った申請2件（一言は未読だが、申請の件数として数える）
        request(mio, me, "相談です");
        request(sora, me, null);
        // 送った申請1件
        request(me, outsider, null);

        get(me, "/api/conversations/summary")
                .andExpect(status().isOk())
                // 申請の一言 + 1通目 + 2通目 + 終了前のメッセージ
                .andExpect(jsonPath("$.unreadMessageCount").value(4))
                .andExpect(jsonPath("$.receivedRequestCount").value(2))
                .andExpect(jsonPath("$.sentRequestCount").value(1));

        // 開くと既読になり、件数が減る
        get(me, "/api/conversations/" + active + "/messages").andExpect(status().isOk());
        get(me, "/api/conversations/summary").andExpect(jsonPath("$.unreadMessageCount").value(1));
    }

    // ---- 部品 ----

    private User saveUser(int index, String username, String displayName) {
        User user = new User(DmTestSupport.EMAILS[index], "0900000007" + index, "hash", username);
        if (displayName != null) {
            user.updateProfile(displayName, null, null, null, null, null);
        }
        return userRepository.save(user);
    }

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

    private void accept(User user, long id) throws Exception {
        mockMvc.perform(post("/api/conversations/" + id + "/accept").header("Authorization", bearer(user)))
                .andExpect(status().isOk());
    }

    private void end(User user, long id) throws Exception {
        mockMvc.perform(post("/api/conversations/" + id + "/end").header("Authorization", bearer(user)))
                .andExpect(status().isOk());
    }

    private void send(User sender, long id, String body) throws Exception {
        mockMvc.perform(multipart("/api/conversations/" + id + "/messages")
                        .param("body", body)
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isCreated());
    }

    private ResultActions get(User viewer, String url) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", bearer(viewer)));
    }

    private List<Long> listedIds(User viewer, String query) throws Exception {
        String body = get(viewer, "/api/conversations" + query)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.conversations[*].conversationId");
        return ids.stream().map(Number::longValue).toList();
    }

    /** 検索語は param で渡す（URL に直接書くと、MockMvc がもう一度エンコードしてしまうため） */
    private List<Long> searchedIds(User viewer, String q) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/conversations")
                        .param("q", q)
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.conversations[*].conversationId");
        return ids.stream().map(Number::longValue).toList();
    }

    private String bearer(User user) {
        return "Bearer " + DmTestSupport.token(jwtEncoder, user.getId());
    }
}
