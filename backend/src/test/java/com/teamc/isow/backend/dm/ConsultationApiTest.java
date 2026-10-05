package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 相談の申込（POST /api/conversations）と、申し込めるかの確認（GET /api/users/{id}/consultation-status）。
 * API がトランザクションを自分で管理するため、テストはロールバックせず、終了時に作ったデータを消す。
 * 上限はテスト用の設定（app.dm.max-received-active-conversations=3）で確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsultationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User userA;
    private User userB;
    private User userC;
    private User userD;
    private User userE;

    @BeforeEach
    void setUp() {
        userA = saveUser(0, "dm_a");
        userB = saveUser(1, "dm_b");
        userC = saveUser(2, "dm_c");
        userD = saveUser(3, "dm_d");
        userE = saveUser(4, "dm_e");
    }

    @AfterEach
    void tearDown() {
        DmTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- 認証・入力 ----

    @Test
    void トークンがない場合は401() throws Exception {
        mockMvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON)
                        .content(json(userB.getId(), null)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(statusUrl(userB))).andExpect(status().isUnauthorized());
    }

    @Test
    void 相手の指定がない場合は400() throws Exception {
        apply(userA, "{\"message\":\"よろしく\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.recipientId").value("相談する相手を指定してください"));
        assertThat(conversationCount()).isZero();
    }

    @Test
    void 一言メッセージが1000文字を超える場合は400() throws Exception {
        apply(userA, json(userB.getId(), "あ".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").value("メッセージは1000文字以内で入力してください"));
        assertThat(conversationCount()).isZero();
    }

    @Test
    void 存在しない相手には404() throws Exception {
        apply(userA, json(999_999L, null)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/999999/consultation-status").header("Authorization", bearer(userA)))
                .andExpect(status().isNotFound());
    }

    // ---- 申込の成功 ----

    @Test
    void 申し込むと申請中の会話ができ_一言メッセージが最初のメッセージになる() throws Exception {
        apply(userB, json(userA.getId(), "  コーデの相談をさせてください  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").isNumber())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requestedAt").isNotEmpty());

        Map<String, Object> conversation = jdbcTemplate.queryForMap("SELECT * FROM conversations");
        // ID の小さい方が user1。申し込んだのは B
        assertThat(conversation.get("USER1_ID")).isEqualTo(userA.getId());
        assertThat(conversation.get("USER2_ID")).isEqualTo(userB.getId());
        assertThat(conversation.get("REQUESTED_BY_ID")).isEqualTo(userB.getId());
        assertThat(conversation.get("STATUS")).isEqualTo("REQUESTED");

        Map<String, Object> message = jdbcTemplate.queryForMap("SELECT * FROM messages");
        assertThat(message.get("CONVERSATION_ID")).isEqualTo(conversation.get("ID"));
        assertThat(message.get("SENDER_ID")).isEqualTo(userB.getId());
        assertThat(message.get("BODY")).isEqualTo("コーデの相談をさせてください");
        assertThat(message.get("READ_AT")).isNull();
        assertThat(conversation.get("LAST_MESSAGE_AT")).isEqualTo(message.get("SENT_AT"));
    }

    @Test
    void 一言メッセージがない_または空白だけの場合はメッセージを保存しない() throws Exception {
        apply(userA, json(userB.getId(), null)).andExpect(status().isCreated());
        apply(userA, json(userC.getId(), "   ")).andExpect(status().isCreated());

        assertThat(conversationCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM messages", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM conversations WHERE last_message_at IS NULL", Integer.class)).isEqualTo(2);
    }

    @Test
    void 何もなければ申し込める() throws Exception {
        checkStatus(userA, userB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").isEmpty())
                .andExpect(jsonPath("$.message").isEmpty())
                .andExpect(jsonPath("$.availableAt").isEmpty())
                .andExpect(jsonPath("$.conversationId").isEmpty());
    }

    // ---- 申し込めない場合 ----

    @Test
    void 自分自身には申し込めない() throws Exception {
        expectUnavailable(userA, userA, 400, "SELF", "自分自身には相談を申し込めません。");
        assertThat(conversationCount()).isZero();
    }

    @Test
    void 自分の申請が申請中なら申し込めず_相手側からは相談が届いていると分かる() throws Exception {
        long id = applyAndGetId(userA, userB);

        expectUnavailable(userA, userB, 409, "ALREADY_REQUESTED", "すでに相談を申し込んでいます。");
        checkStatus(userA, userB).andExpect(jsonPath("$.conversationId").value(id));
        // 逆向き（B から A）でも同じ2人の会話として扱う
        expectUnavailable(userB, userA, 409, "REQUEST_RECEIVED", "この相手から相談が届いています。");
        checkStatus(userB, userA).andExpect(jsonPath("$.conversationId").value(id));
        assertThat(conversationCount()).isEqualTo(1);
    }

    @Test
    void 進行中の会話があれば_どちらからも申し込めない() throws Exception {
        long id = applyAndGetId(userA, userB);
        change(id, Conversation::approve);

        expectUnavailable(userA, userB, 409, "IN_PROGRESS", "この相手とは相談中です。");
        expectUnavailable(userB, userA, 409, "IN_PROGRESS", "この相手とは相談中です。");
        checkStatus(userA, userB).andExpect(jsonPath("$.conversationId").value(id));
    }

    @Test
    void 終了した会話があっても申し込める() throws Exception {
        long id = applyAndGetId(userA, userB);
        change(id, c -> {
            c.approve();
            c.end(c.getUser1());
        });

        apply(userA, json(userB.getId(), null)).andExpect(status().isCreated());
        assertThat(conversationCount()).isEqualTo(2);
    }

    @Test
    void 拒否されてから24時間以内は申し込めず_いつから申し込めるかを返す() throws Exception {
        long id = applyAndGetId(userA, userB);
        change(id, Conversation::reject);
        LocalDateTime rejectedAt = LocalDateTime.now().minusHours(23).withNano(0);
        setRespondedAt(id, rejectedAt);

        expectUnavailable(userA, userB, 409, "REJECTED_RECENTLY",
                "前回の申し込みから24時間は、この相手に再度申し込めません。");
        checkStatus(userA, userB)
                .andExpect(jsonPath("$.availableAt").value(rejectedAt.plusHours(24).toString()))
                .andExpect(jsonPath("$.conversationId").isEmpty());
        assertThat(conversationCount()).isEqualTo(1);
    }

    @Test
    void 拒否した側からは24時間以内でも申し込める() throws Exception {
        long id = applyAndGetId(userA, userB);
        change(id, Conversation::reject);

        apply(userB, json(userA.getId(), null)).andExpect(status().isCreated());
    }

    @Test
    void 拒否から24時間を過ぎれば再申請でき_拒否された会話は履歴として残る() throws Exception {
        // 拒否された会話が2件ある状態で再申請する（ongoing が NULL の行がある組み合わせへの INSERT）
        for (int i = 0; i < 2; i++) {
            long rejected = applyAndGetId(userA, userB);
            change(rejected, Conversation::reject);
            setRespondedAt(rejected, LocalDateTime.now().minusHours(25));
        }

        apply(userA, json(userB.getId(), "もう一度お願いします")).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM conversations WHERE status = 'REJECTED'", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM conversations WHERE status = 'REQUESTED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 相手が受けている進行中の会話が上限に達していると申し込めない() throws Exception {
        for (User requester : new User[] {userC, userD, userE}) {
            change(applyAndGetId(requester, userB), Conversation::approve);
        }

        expectUnavailable(userA, userB, 409, "LIMIT_REACHED", "現在、新しい相談を受け付けていません。");
        assertThat(conversationCount()).isEqualTo(3);
    }

    @Test
    void 相手が自分から申し込んだ会話と_申請中の会話は上限に数えない() throws Exception {
        // B が受けている進行中は2件
        change(applyAndGetId(userC, userB), Conversation::approve);
        change(applyAndGetId(userD, userB), Conversation::approve);
        // B から申し込んだ進行中の会話（数えない）
        change(applyAndGetId(userB, userE), Conversation::approve);
        // B が受けている申請中の会話（数えない）
        applyAndGetId(saveUser(5, "dm_f"), userB);

        checkStatus(userA, userB).andExpect(jsonPath("$.available").value(true));
        apply(userA, json(userB.getId(), null)).andExpect(status().isCreated());
    }

    // ---- 部品 ----

    private User saveUser(int index, String username) {
        return userRepository.save(new User(DmTestSupport.EMAILS[index], "0900000003" + index, "hash", username));
    }

    /** POST と GET の両方で、同じ理由で申し込めないことを確認する */
    private void expectUnavailable(User requester, User recipient, int httpStatus, String reason, String message)
            throws Exception {
        apply(requester, json(recipient.getId(), "よろしくお願いします"))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.reason").value(reason))
                .andExpect(jsonPath("$.message").value(message));
        checkStatus(requester, recipient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(reason))
                .andExpect(jsonPath("$.message").value(message));
    }

    private long applyAndGetId(User requester, User recipient) throws Exception {
        String body = apply(requester, json(recipient.getId(), null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"conversationId\":(\\d+).*", "$1"));
    }

    /** 承認・拒否などの API はまだないため、エンティティの操作で状態を変える */
    private void change(long conversationId, Consumer<Conversation> action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> action.accept(conversationRepository.findById(conversationId).orElseThrow()));
    }

    private void setRespondedAt(long conversationId, LocalDateTime respondedAt) {
        jdbcTemplate.update("UPDATE conversations SET responded_at = ? WHERE id = ?",
                Timestamp.valueOf(respondedAt), conversationId);
    }

    private ResultActions apply(User requester, String body) throws Exception {
        return mockMvc.perform(post("/api/conversations")
                .header("Authorization", bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions checkStatus(User viewer, User target) throws Exception {
        return mockMvc.perform(get(statusUrl(target)).header("Authorization", bearer(viewer)));
    }

    private String bearer(User user) {
        return "Bearer " + DmTestSupport.token(jwtEncoder, user.getId());
    }

    private int conversationCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM conversations", Integer.class);
    }

    private static String statusUrl(User user) {
        return "/api/users/" + user.getId() + "/consultation-status";
    }

    private static String json(Long recipientId, String message) {
        String messagePart = message == null ? "null" : "\"" + message + "\"";
        return "{\"recipientId\":" + recipientId + ",\"message\":" + messagePart + "}";
    }
}
