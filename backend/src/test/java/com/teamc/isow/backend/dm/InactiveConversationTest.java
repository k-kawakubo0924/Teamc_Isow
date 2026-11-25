package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
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
 * 30日やり取りがない相談の自動終了（docs/dm.md「30日やり取りがない相談の自動終了」）。
 *
 * <p>処理本体（InactiveConversationCloser）に固定した今の時刻を渡して直接呼ぶ。最後のやり取りの日時は DB に直接書く。
 * 時刻は DB の現在時刻ではなく、アプリの時刻を引数で渡す（admin.md「時刻の扱い」）
 */
@SpringBootTest
@AutoConfigureMockMvc
class InactiveConversationTest {

    /** テストの「今」。日本時間の昼（DB の現在時刻と9時間ずれていても、結果が変わらないことを確かめるため固定する） */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InactiveConversationCloser closer;

    private User requester;
    private User recipient;

    @BeforeEach
    void setUp() {
        requester = saveUser(0, "dm_a");
        recipient = saveUser(1, "dm_b");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM moderation_actions");
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
        jdbcTemplate.update("DELETE FROM blocks");
        DmTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- どこから数えるか ----

    @Test
    void 進行中は_最後のやり取りから30日ちょうどで終了し_1分足りなければ終了しない() throws Exception {
        long exactly = active(requester, recipient);
        setActivity(exactly, NOW.minusDays(40), NOW.minusDays(30));
        User other = saveUser(2, "dm_c");
        long notYet = active(other, recipient);
        setActivity(notYet, NOW.minusDays(40), NOW.minusDays(30).plusMinutes(1));

        assertThat(closer.closeInactive(NOW).closed()).isEqualTo(1);

        assertThat(statusOf(exactly)).isEqualTo("ENDED");
        assertThat(statusOf(notYet)).isEqualTo("ACTIVE");
    }

    @Test
    void 承認の日時のほうが新しければ_承認から数える() throws Exception {
        // 申し込みの一言が40日前で、承認が10日前。承認の後は誰も書いていない
        long id = active(requester, recipient);
        setActivity(id, NOW.minusDays(10), NOW.minusDays(40));

        assertThat(closer.closeInactive(NOW).closed()).isZero();
        assertThat(statusOf(id)).isEqualTo("ACTIVE");
    }

    @Test
    void メッセージがなければ承認から数える() throws Exception {
        long id = active(requester, recipient);
        setActivity(id, NOW.minusDays(31), null);

        closer.closeInactive(NOW);

        assertThat(statusOf(id)).isEqualTo("ENDED");
    }

    @Test
    void 申請中のまま30日たった相談は_拒否ではなく終了にする() throws Exception {
        long old = request(requester, recipient);
        jdbcTemplate.update("UPDATE conversations SET requested_at = ?, last_message_at = ? WHERE id = ?",
                Timestamp.valueOf(NOW.minusDays(30)), Timestamp.valueOf(NOW.minusDays(30)), old);
        User other = saveUser(2, "dm_c");
        long recent = request(other, recipient);
        jdbcTemplate.update("UPDATE conversations SET requested_at = ? WHERE id = ?",
                Timestamp.valueOf(NOW.minusDays(29)), recent);

        closer.closeInactive(NOW);

        assertThat(statusOf(old)).isEqualTo("ENDED");
        assertThat(statusOf(recent)).isEqualTo("REQUESTED");
        // 終了したので、申し込んだ人はもう一度申し込める
        mockMvc.perform(get("/api/users/" + recipient.getId() + "/consultation-status").header("Authorization",
                        bearer(requester)))
                .andExpect(jsonPath("$.available").value(true));
    }

    // ---- 終了の記録 ----

    @Test
    void 終了した人は空で_理由は自動終了_終了日時は渡した今の時刻() throws Exception {
        long id = active(requester, recipient);
        setActivity(id, NOW.minusDays(60), NOW.minusDays(45));

        closer.closeInactive(NOW);

        Map<String, Object> row = row(id);
        assertThat(row.get("STATUS")).isEqualTo("ENDED");
        assertThat(row.get("ONGOING")).isNull();
        assertThat(row.get("ENDED_BY_ID")).isNull();
        assertThat(row.get("END_REASON")).isEqualTo("INACTIVITY");
        assertThat(((Timestamp) row.get("ENDED_AT")).toLocalDateTime()).isEqualTo(NOW);
    }

    @Test
    void 通知は作らない() throws Exception {
        long id = active(requester, recipient);
        setActivity(id, NOW.minusDays(60), NOW.minusDays(45));
        long before = count("SELECT count(*) FROM notifications");

        closer.closeInactive(NOW);

        assertThat(statusOf(id)).isEqualTo("ENDED");
        assertThat(count("SELECT count(*) FROM notifications")).isEqualTo(before);
    }

    @Test
    void 二重に処理しない_2回目は何もせず_すでに終了した相談の記録も変えない() throws Exception {
        long inactive = active(requester, recipient);
        setActivity(inactive, NOW.minusDays(60), NOW.minusDays(45));
        User other = saveUser(2, "dm_c");
        long endedByUser = active(other, recipient);
        operate(recipient, endedByUser, "end").andExpect(status().isOk());
        setActivity(endedByUser, NOW.minusDays(60), NOW.minusDays(45));
        Object endedAtByUser = row(endedByUser).get("ENDED_AT");

        assertThat(closer.closeInactive(NOW).closed()).isEqualTo(1);
        assertThat(closer.closeInactive(NOW.plusHours(1)).closed()).isZero();

        assertThat(((Timestamp) row(inactive).get("ENDED_AT")).toLocalDateTime()).isEqualTo(NOW);
        assertThat(row(endedByUser).get("END_REASON")).isEqualTo("USER");
        assertThat(row(endedByUser).get("ENDED_AT")).isEqualTo(endedAtByUser);
        assertThat(row(endedByUser).get("ENDED_BY_ID")).isEqualTo(recipient.getId());
    }

    @Test
    void 終了すると受けている相談の枠が空く() throws Exception {
        // 上限に達すると申し込みもできないため、待っている申請を先に作っておく
        long waiting = request(saveUser(4, "dm_e"), recipient);
        long stale = active(requester, recipient);
        setActivity(stale, NOW.minusDays(60), NOW.minusDays(45));
        active(saveUser(2, "dm_c"), recipient);
        active(saveUser(3, "dm_d"), recipient);
        operate(recipient, waiting, "accept").andExpect(status().isConflict());

        closer.closeInactive(NOW);

        operate(recipient, waiting, "accept").andExpect(status().isOk());
    }

    // ---- 画面に返すのは「自動終了かどうか」だけ ----

    @Test
    void 自動終了した相談は_当事者の両方に自動終了と分かる() throws Exception {
        long id = active(requester, recipient);
        setActivity(id, NOW.minusDays(60), NOW.minusDays(45));
        closer.closeInactive(NOW);

        for (User user : new User[] {requester, recipient}) {
            mockMvc.perform(get("/api/conversations/" + id).header("Authorization", bearer(user)))
                    .andExpect(jsonPath("$.status").value("ENDED"))
                    .andExpect(jsonPath("$.endedByInactivity").value(true));
        }
    }

    @Test
    void 本人_ブロック_利用停止で終了した相談は_理由を返さず_自動終了でないことだけを返す() throws Exception {
        // 本人が終了
        long byUser = active(requester, recipient);
        operate(requester, byUser, "end").andExpect(status().isOk());
        // ブロックで終了
        User blocked = saveUser(2, "dm_c");
        long byBlock = active(blocked, recipient);
        mockMvc.perform(post("/api/users/" + blocked.getId() + "/block").header("Authorization", bearer(recipient)))
                .andExpect(status().is2xxSuccessful());
        // 利用停止で終了（管理者の措置）
        User suspended = saveUser(3, "dm_d");
        long bySuspension = active(suspended, recipient);
        User admin = saveUser(5, "dm_f");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        mockMvc.perform(post("/api/admin/moderation/users/" + suspended.getId() + "/suspend")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"SPAM\", \"days\": 1, \"memo\": \"\"}"))
                .andExpect(status().isOk());

        assertThat(row(byUser).get("END_REASON")).isEqualTo("USER");
        assertThat(row(byBlock).get("END_REASON")).isEqualTo("BLOCK");
        assertThat(row(bySuspension).get("END_REASON")).isEqualTo("SUSPENSION");
        for (long id : new long[] {byUser, byBlock, bySuspension}) {
            String detail = mockMvc.perform(get("/api/conversations/" + id).header("Authorization", bearer(recipient)))
                    .andExpect(jsonPath("$.status").value("ENDED"))
                    .andExpect(jsonPath("$.endedByInactivity").value(false))
                    .andReturn().getResponse().getContentAsString();
            assertNoReason(detail);
        }
        String list = mockMvc.perform(get("/api/conversations?status=chats").header("Authorization", bearer(recipient)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNoReason(list);
    }

    // ---- 定期実行 ----

    @Test
    void 定期実行は_アプリの時計の今の時刻を処理に渡す() {
        InactiveConversationCloser mockCloser = mock(InactiveConversationCloser.class);
        when(mockCloser.closeInactive(any())).thenReturn(new InactiveConversationCloser.Result(0, 0));
        Clock fixed = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Tokyo")).toInstant(), ZoneId.of("Asia/Tokyo"));

        new InactiveConversationJob(mockCloser, fixed).run();

        verify(mockCloser).closeInactive(NOW);
    }

    @Test
    void 定期実行は失敗しても例外を外に投げない() {
        InactiveConversationCloser mockCloser = mock(InactiveConversationCloser.class);
        when(mockCloser.closeInactive(any())).thenThrow(new IllegalStateException("テストの失敗"));

        assertThatCode(() -> new InactiveConversationJob(mockCloser, Clock.systemDefaultZone()).run())
                .doesNotThrowAnyException();
    }

    // ---- 部品 ----

    private static void assertNoReason(String json) {
        assertThat(json).doesNotContain("endReason", "BLOCK", "SUSPENSION", "INACTIVITY");
    }

    private User saveUser(int index, String username) {
        return userRepository.save(new User(DmTestSupport.EMAILS[index], "0900000006" + index, "hash", username));
    }

    /** 相談を申し込み、会話のIDを返す */
    private long request(User from, User to) throws Exception {
        String body = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", bearer(from))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":" + to.getId() + ", \"message\": \"よろしくお願いします\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"conversationId\":(\\d+).*", "$1"));
    }

    /** 申し込んで承認し、進行中の会話のIDを返す */
    private long active(User from, User to) throws Exception {
        long id = request(from, to);
        operate(to, id, "accept").andExpect(status().isOk());
        return id;
    }

    /** 承認の日時と最終メッセージの日時を書き換える（lastMessageAt が null ならメッセージなし） */
    private void setActivity(long id, LocalDateTime respondedAt, LocalDateTime lastMessageAt) {
        jdbcTemplate.update("UPDATE conversations SET requested_at = ?, responded_at = ?, last_message_at = ? WHERE id = ?",
                Timestamp.valueOf(respondedAt.minusDays(1)), Timestamp.valueOf(respondedAt),
                lastMessageAt == null ? null : Timestamp.valueOf(lastMessageAt), id);
    }

    private ResultActions operate(User user, long conversationId, String operation) throws Exception {
        return mockMvc.perform(post("/api/conversations/" + conversationId + "/" + operation)
                .header("Authorization", bearer(user)));
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

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
