package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 会話の承認・拒否・終了（POST /api/conversations/{id}/accept・reject・end）。
 * API がトランザクションを自分で管理するため、テストはロールバックせず、終了時に作ったデータを消す。
 * 上限はテスト用の設定（app.dm.max-received-active-conversations=3）で確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConversationStatusApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 申し込む側 */
    private User requester;
    /** 申し込まれる側 */
    private User recipient;
    /** 当事者でない人 */
    private User outsider;

    @BeforeEach
    void setUp() {
        requester = saveUser(0, "dm_a");
        recipient = saveUser(1, "dm_b");
        outsider = saveUser(2, "dm_c");
    }

    @AfterEach
    void tearDown() {
        DmTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- 認証・当事者 ----

    @Test
    void トークンがない場合は401() throws Exception {
        long id = request(requester, recipient);
        for (String operation : new String[] {"accept", "reject", "end"}) {
            mockMvc.perform(post(url(id, operation))).andExpect(status().isUnauthorized());
        }
        assertThat(statusOf(id)).isEqualTo("REQUESTED");
    }

    @Test
    void 当事者でない人には_存在しない会話と同じ404を返す() throws Exception {
        long id = request(requester, recipient);
        for (String operation : new String[] {"accept", "reject", "end"}) {
            String notParticipant = operate(outsider, id, operation)
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            String notExists = operate(outsider, 999_999L, operation)
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            assertThat(notParticipant).isEqualTo(notExists).contains("会話が見つかりません。");
        }
        assertThat(statusOf(id)).isEqualTo("REQUESTED");
    }

    // ---- 承認 ----

    @Test
    void 申し込まれた側が承認すると進行中になり_承認した日時が記録される() throws Exception {
        long id = request(requester, recipient);

        operate(recipient, id, "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(id))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.respondedAt").isNotEmpty())
                .andExpect(jsonPath("$.endedAt").isEmpty());

        Map<String, Object> row = row(id);
        assertThat(row.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(row.get("ONGOING")).isEqualTo(true);
        assertThat(row.get("RESPONDED_AT")).isNotNull();
    }

    @Test
    void 申し込んだ本人は承認も拒否もできない() throws Exception {
        long id = request(requester, recipient);

        expectError(operate(requester, id, "accept"), 403, "NOT_RECIPIENT", "申し込んだ本人は承認できません。");
        expectError(operate(requester, id, "reject"), 403, "NOT_RECIPIENT", "申し込んだ本人は拒否できません。");
        assertThat(statusOf(id)).isEqualTo("REQUESTED");
    }

    @Test
    void 受けている進行中の会話が上限に達していると承認できない() throws Exception {
        // 上限に達する前に届いていた申請
        long pending = request(requester, recipient);
        for (int i = 3; i <= 5; i++) {
            long id = request(saveUser(i, "dm_" + i), recipient);
            operate(recipient, id, "accept").andExpect(status().isOk());
        }

        expectError(operate(recipient, pending, "accept"), 409, "LIMIT_REACHED",
                "相談を受けられる上限（3件）に達しているため、承認できません。進行中の相談を終了してから承認してください。");
        assertThat(statusOf(pending)).isEqualTo("REQUESTED");
        // 拒否は上限に関係なくできる
        operate(recipient, pending, "reject").andExpect(status().isOk());
    }

    @Test
    void 残り1件の状態で2件を同時に承認しても_上限を超えない() throws Exception {
        // 受けている進行中が2件（上限3）、申請中が2件
        for (int i = 3; i <= 4; i++) {
            long id = request(saveUser(i, "dm_" + i), recipient);
            operate(recipient, id, "accept").andExpect(status().isOk());
        }
        long[] pending = {request(requester, recipient), request(outsider, recipient)};

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long id : pending) {
                results.add(executor.submit(() -> {
                    start.await();
                    return operate(recipient, id, "accept").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM conversations WHERE status = 'ACTIVE'", Integer.class)).isEqualTo(3);
    }

    @Test
    void 自分から申し込んだ進行中の会話は_承認の上限に数えない() throws Exception {
        // recipient から申し込んだ進行中の会話が3件
        for (int i = 3; i <= 5; i++) {
            User other = saveUser(i, "dm_" + i);
            operate(other, request(recipient, other), "accept").andExpect(status().isOk());
        }
        long pending = request(requester, recipient);

        operate(recipient, pending, "accept").andExpect(status().isOk());
    }

    @Test
    void 終了した会話の分は_上限の空きになる() throws Exception {
        long pending = request(requester, recipient);
        long first = 0;
        for (int i = 3; i <= 5; i++) {
            long id = request(saveUser(i, "dm_" + i), recipient);
            operate(recipient, id, "accept").andExpect(status().isOk());
            first = first == 0 ? id : first;
        }
        expectError(operate(recipient, pending, "accept"), 409, "LIMIT_REACHED",
                "相談を受けられる上限（3件）に達しているため、承認できません。進行中の相談を終了してから承認してください。");

        operate(recipient, first, "end").andExpect(status().isOk());

        operate(recipient, pending, "accept").andExpect(status().isOk());
    }

    // ---- 拒否 ----

    @Test
    void 申し込まれた側が拒否すると拒否になり_拒否した日時が記録される() throws Exception {
        long id = request(requester, recipient);

        operate(recipient, id, "reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty())
                .andExpect(jsonPath("$.endedAt").isEmpty());

        Map<String, Object> row = row(id);
        assertThat(row.get("STATUS")).isEqualTo("REJECTED");
        assertThat(row.get("ONGOING")).isNull();
        assertThat(row.get("RESPONDED_AT")).isNotNull();
        assertThat(row.get("ENDED_BY_ID")).isNull();
    }

    @Test
    void 拒否した日時から24時間は_申し込んだ人が再申請できなくなる() throws Exception {
        long id = request(requester, recipient);
        operate(recipient, id, "reject").andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + recipient.getId() + "/consultation-status")
                        .header("Authorization", bearer(requester)))
                .andExpect(jsonPath("$.reason").value("REJECTED_RECENTLY"));
    }

    // ---- 終了 ----

    @Test
    void 進行中の会話は_どちらからでも終了できる() throws Exception {
        for (User ender : new User[] {requester, recipient}) {
            long id = request(requester, recipient);
            operate(recipient, id, "accept").andExpect(status().isOk());

            operate(ender, id, "end")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ENDED"))
                    .andExpect(jsonPath("$.endedAt").isNotEmpty());

            Map<String, Object> row = row(id);
            assertThat(row.get("STATUS")).isEqualTo("ENDED");
            assertThat(row.get("ONGOING")).isNull();
            assertThat(row.get("ENDED_AT")).isNotNull();
            assertThat(row.get("ENDED_BY_ID")).isEqualTo(ender.getId());
        }
    }

    // ---- 状態が合わない操作 ----

    @Test
    void 申請中の会話は終了できない() throws Exception {
        long id = request(requester, recipient);

        expectError(operate(requester, id, "end"), 409, "INVALID_STATUS", "この会話は申請中のため、終了できません。");
        assertThat(statusOf(id)).isEqualTo("REQUESTED");
    }

    @Test
    void 進行中の会話は承認も拒否もできない() throws Exception {
        long id = request(requester, recipient);
        operate(recipient, id, "accept").andExpect(status().isOk());

        expectError(operate(recipient, id, "accept"), 409, "INVALID_STATUS", "この会話は進行中のため、承認できません。");
        expectError(operate(recipient, id, "reject"), 409, "INVALID_STATUS", "この会話は進行中のため、拒否できません。");
        assertThat(statusOf(id)).isEqualTo("ACTIVE");
    }

    @Test
    void 終了した会話は承認も終了もできない() throws Exception {
        long id = request(requester, recipient);
        operate(recipient, id, "accept").andExpect(status().isOk());
        operate(requester, id, "end").andExpect(status().isOk());

        expectError(operate(recipient, id, "accept"), 409, "INVALID_STATUS", "この会話は終了のため、承認できません。");
        expectError(operate(recipient, id, "end"), 409, "INVALID_STATUS", "この会話は終了のため、終了できません。");
        assertThat(statusOf(id)).isEqualTo("ENDED");
    }

    @Test
    void 拒否した会話は承認も終了もできない() throws Exception {
        long id = request(requester, recipient);
        operate(recipient, id, "reject").andExpect(status().isOk());

        expectError(operate(recipient, id, "accept"), 409, "INVALID_STATUS", "この会話は拒否のため、承認できません。");
        expectError(operate(recipient, id, "end"), 409, "INVALID_STATUS", "この会話は拒否のため、終了できません。");
        assertThat(statusOf(id)).isEqualTo("REJECTED");
    }

    // ---- 部品 ----

    private User saveUser(int index, String username) {
        return userRepository.save(new User(DmTestSupport.EMAILS[index], "0900000004" + index, "hash", username));
    }

    /** 相談を申し込み、会話のIDを返す */
    private long request(User from, User to) throws Exception {
        String body = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", bearer(from))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":" + to.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"conversationId\":(\\d+).*", "$1"));
    }

    private ResultActions operate(User user, long conversationId, String operation) throws Exception {
        return mockMvc.perform(post(url(conversationId, operation)).header("Authorization", bearer(user)));
    }

    private static void expectError(ResultActions result, int httpStatus, String reason, String message)
            throws Exception {
        result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.reason").value(reason))
                .andExpect(jsonPath("$.message").value(message));
    }

    private String bearer(User user) {
        return "Bearer " + DmTestSupport.token(jwtEncoder, user.getId());
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM conversations WHERE id = ?", id);
    }

    private String statusOf(long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM conversations WHERE id = ?", String.class, id);
    }

    private static String url(long conversationId, String operation) {
        return "/api/conversations/" + conversationId + "/" + operation;
    }
}
