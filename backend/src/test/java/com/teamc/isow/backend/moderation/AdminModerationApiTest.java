package com.teamc.isow.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.admin.AdminOperationLogRepository;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 管理者の措置（/api/admin/moderation/**。docs/admin.md「6. 措置」）の確認。
 * 投稿の非公開化・公開に戻す、ユーザーの利用停止・解除、通報との結びつき、操作ログ・措置の履歴・本人への通知を確かめる
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationApiTest {

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private AdminOperationLogRepository logRepository;

    private ModerationFixture f;

    @BeforeEach
    void setUp() {
        f = new ModerationFixture(mockMvc, jwtEncoder, userRepository, postRepository, fashionCategoryRepository,
                tagRepository, jdbcTemplate, passwordEncoder);
        f.createUsers();
    }

    @AfterEach
    void tearDown() {
        f.cleanUp();
    }

    // ---- 投稿の非公開化 ----

    @Test
    void 投稿を非公開にすると_状態と履歴とログが残り_投稿者に通知する_ログには理由だけを書く() throws Exception {
        Post post = f.createPost(f.author, "非公開にする投稿");

        hide(post.getId(), "SPAM", "メモ：宣伝が続いている")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.hiddenReason").value("SPAM"))
                .andExpect(jsonPath("$.history[0].action").value("HIDE_POST"))
                .andExpect(jsonPath("$.history[0].adminUsername").value("mod_admin"))
                .andExpect(jsonPath("$.history[0].memo").value("メモ：宣伝が続いている"));

        Map<String, Object> saved = jdbcTemplate.queryForMap("SELECT * FROM posts WHERE id = ?", post.getId());
        assertThat(saved.get("hidden_at")).isNotNull();
        assertThat(saved.get("hidden_reason")).isEqualTo("SPAM");
        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("action")).isEqualTo("POST_HIDDEN");
        assertThat(log.get("target_type")).isEqualTo("POST");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(post.getId());
        assertThat((String) log.get("detail")).contains("スパム・宣伝").doesNotContain("メモ", "非公開にする投稿", "mod_");
        Map<String, Object> notification = jdbcTemplate.queryForMap(
                "SELECT * FROM notifications WHERE recipient_id = ?", f.author.getId());
        assertThat(notification.get("type")).isEqualTo("POST_HIDDEN");
        assertThat(notification.get("actor_id")).isNull();
        assertThat(((Number) notification.get("post_id")).longValue()).isEqualTo(post.getId());
    }

    @Test
    void 非公開を公開に戻すと_状態が消え_ログと通知が残る() throws Exception {
        Post post = f.createPost(f.author, "公開に戻す投稿");
        hide(post.getId(), "SPAM", null).andExpect(status().isOk());

        unhide(post.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(false))
                .andExpect(jsonPath("$.history[0].action").value("UNHIDE_POST"));

        assertThat(jdbcTemplate.queryForObject("SELECT hidden_at FROM posts WHERE id = ?", Object.class, post.getId()))
                .isNull();
        assertThat(jdbcTemplate.queryForList("SELECT action FROM admin_operation_logs ORDER BY id", String.class))
                .containsExactly("POST_HIDDEN", "POST_UNHIDDEN");
        assertThat(jdbcTemplate.queryForList("SELECT type FROM notifications WHERE recipient_id = ? ORDER BY id",
                String.class, f.author.getId())).containsExactly("POST_HIDDEN", "POST_UNHIDDEN");
        f.as(f.viewer, get("/api/posts/" + post.getId())).andExpect(status().isOk());
    }

    @Test
    void 非公開のものをまた非公開にする_公開のものを公開に戻すは409_存在しない投稿は404_理由の誤りは400() throws Exception {
        Post post = f.createPost(f.author, "状態の確認の投稿");

        unhide(post.getId()).andExpect(status().isConflict());
        hide(post.getId(), "SPAM", null).andExpect(status().isOk());
        hide(post.getId(), "SPAM", null).andExpect(status().isConflict());
        hide(999999999L, "SPAM", null).andExpect(status().isNotFound());
        hide(post.getId(), "NO_SUCH_REASON", null).andExpect(status().isBadRequest());
        assertThat(f.count("SELECT count(*) FROM admin_operation_logs")).isEqualTo(1);
    }

    @Test
    void ログを残せなければ非公開にもならない() throws Exception {
        Post post = f.createPost(f.author, "ログ失敗の投稿");
        doThrow(new IllegalStateException("ログの保存に失敗（テスト）")).when(logRepository).save(any());

        assertThatThrownBy(() -> hide(post.getId(), "SPAM", null)).hasRootCauseMessage("ログの保存に失敗（テスト）");

        assertThat(jdbcTemplate.queryForObject("SELECT hidden_at FROM posts WHERE id = ?", Object.class, post.getId()))
                .isNull();
        assertThat(f.count("SELECT count(*) FROM moderation_actions")).isZero();
    }

    // ---- 利用停止 ----

    @Test
    void 期限つきで利用停止にすると_期限と理由が残り_進行中と申請中の相談は終了し_ログには理由と期間だけを書く() throws Exception {
        long active = f.activeConversation(f.viewer, f.author);
        jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE id = ?", f.admin.getId());
        long requested = f.requestedConversation(f.admin, f.author);
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", f.admin.getId());
        LocalDateTime before = LocalDateTime.now();

        suspend(f.author.getId(), "HARASSMENT", 7, "メモ：何度も同じ人に送っている")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.suspensionReason").value("HARASSMENT"))
                .andExpect(jsonPath("$.history[0].action").value("SUSPEND_USER"));

        Map<String, Object> saved = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", f.author.getId());
        assertThat(((Timestamp) saved.get("suspended_until")).toLocalDateTime())
                .isCloseTo(before.plusDays(7), within(1, ChronoUnit.MINUTES));
        assertThat(saved.get("suspension_reason")).isEqualTo("HARASSMENT");
        for (long conversationId : new long[] {active, requested}) {
            Map<String, Object> conversation = jdbcTemplate.queryForMap("SELECT * FROM conversations WHERE id = ?",
                    conversationId);
            assertThat(conversation.get("status")).isEqualTo("ENDED");
            assertThat(conversation.get("ended_by_id")).isNull();
        }
        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("action")).isEqualTo("USER_SUSPENDED");
        assertThat(log.get("target_type")).isEqualTo("USER");
        assertThat((String) log.get("detail")).contains("嫌がらせ・誹謗中傷", "7日").doesNotContain("メモ", "mod_");
    }

    @Test
    void 無期限の利用停止は期限が空() throws Exception {
        suspend(f.author.getId(), "SPAM", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedUntil").value(nullValue()));

        assertThat(jdbcTemplate.queryForObject("SELECT suspended_at FROM users WHERE id = ?", Object.class,
                f.author.getId())).isNotNull();
        assertThat((String) jdbcTemplate.queryForObject("SELECT detail FROM admin_operation_logs", String.class))
                .contains("無期限");
    }

    @Test
    void 利用停止を解除すると使えるようになるが_終了した相談は戻らない() throws Exception {
        long active = f.activeConversation(f.viewer, f.author);
        suspend(f.author.getId(), "SPAM", 30, null).andExpect(status().isOk());

        unsuspend(f.author.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(false))
                .andExpect(jsonPath("$.history[0].action").value("UNSUSPEND_USER"));

        f.as(f.author, get("/api/users/me")).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM conversations WHERE id = ?", String.class, active))
                .isEqualTo("ENDED");
        assertThat(jdbcTemplate.queryForList("SELECT action FROM admin_operation_logs ORDER BY id", String.class))
                .containsExactly("USER_SUSPENDED", "USER_UNSUSPENDED");
    }

    @Test
    void 期間は1日_3日_7日_30日か無期限だけ_管理者と自分自身は利用停止にできない_停止中をまた停止にはできない() throws Exception {
        suspend(f.author.getId(), "SPAM", 5, null).andExpect(status().isBadRequest());
        suspend(f.admin.getId(), "SPAM", 7, null).andExpect(status().isConflict());
        suspend(f.author.getId(), "SPAM", 7, null).andExpect(status().isOk());
        suspend(f.author.getId(), "SPAM", 7, null).andExpect(status().isConflict());
        unsuspend(f.viewer.getId()).andExpect(status().isConflict());
        suspend(999999999L, "SPAM", 7, null).andExpect(status().isNotFound());
    }

    @Test
    void 期限を過ぎた利用停止は_停止中ではないものとして扱い_また利用停止にできる() throws Exception {
        f.suspend(f.author, LocalDateTime.now().minusMinutes(1));

        unsuspend(f.author.getId()).andExpect(status().isConflict());
        suspend(f.author.getId(), "SPAM", 1, null).andExpect(status().isOk());
    }

    // ---- 通報との結びつき ----

    @Test
    void 通報から措置すると_履歴に通報が結びつき_選べば通報を対応済みにする() throws Exception {
        Post post = f.createPost(f.author, "通報された投稿");
        report("POST", post.getId(), "SPAM");
        long caseId = openCaseId("POST", post.getId());

        f.as(f.admin, post("/api/admin/moderation/posts/" + post.getId() + "/hide").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"SPAM\", \"reportCaseId\": " + caseId + ", \"resolveReport\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].reportCaseId").value(caseId));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("RESOLVED");
        assertThat(f.count("SELECT report_case_id FROM moderation_actions")).isEqualTo(caseId);
        assertThat(jdbcTemplate.queryForList("SELECT action FROM admin_operation_logs ORDER BY id", String.class))
                .containsExactly("POST_HIDDEN", "REPORT_RESOLVED");
    }

    @Test
    void 別の対象の通報を結びつけようとすると400で何もしない() throws Exception {
        Post post = f.createPost(f.author, "措置する投稿");
        Post other = f.createPost(f.author, "別の通報の投稿");
        report("POST", other.getId(), "SPAM");
        long otherCase = openCaseId("POST", other.getId());

        f.as(f.admin, post("/api/admin/moderation/posts/" + post.getId() + "/hide").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"SPAM\", \"reportCaseId\": " + otherCase + "}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT hidden_at FROM posts WHERE id = ?", Object.class, post.getId()))
                .isNull();
    }

    @Test
    void 投稿の通報から投稿者を利用停止にできる() throws Exception {
        Post post = f.createPost(f.author, "投稿者を止める投稿");
        report("POST", post.getId(), "HARASSMENT");
        long caseId = openCaseId("POST", post.getId());

        f.as(f.admin, post("/api/admin/moderation/users/" + f.author.getId() + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"HARASSMENT\", \"days\": 3, \"reportCaseId\": " + caseId + "}"))
                .andExpect(status().isOk());

        assertThat(f.count("SELECT report_case_id FROM moderation_actions")).isEqualTo(caseId);
        // 対応済みにするのを選ばなければ、通報は未対応のまま
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("OPEN");
    }

    // ---- 一覧・探す ----

    @Test
    void 措置中の一覧は_非公開の投稿と利用停止中のユーザーを出し_期限を過ぎたものは出さない() throws Exception {
        Post post = f.createPost(f.author, "一覧の非公開の投稿");
        hide(post.getId(), "SPAM", null).andExpect(status().isOk());
        suspend(f.author.getId(), "SPAM", 7, null).andExpect(status().isOk());
        f.suspend(f.viewer, LocalDateTime.now().minusMinutes(1));

        f.as(f.admin, get("/api/admin/moderation/hidden-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].postId", contains(post.getId().intValue())))
                .andExpect(jsonPath("$.items[0].title").value("一覧の非公開の投稿"));
        f.as(f.admin, get("/api/admin/moderation/suspended-users"))
                .andExpect(jsonPath("$.items[*].userId", contains(f.author.getId().intValue())))
                .andExpect(jsonPath("$.items[0].username").value("mod_author"));
    }

    @Test
    void ユーザー名と投稿IDで探せる() throws Exception {
        Post post = f.createPost(f.author, "探す投稿");

        f.as(f.admin, get("/api/admin/moderation/users").param("username", "mod_author"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(f.author.getId()))
                .andExpect(jsonPath("$.suspended").value(false));
        f.as(f.admin, get("/api/admin/moderation/posts/" + post.getId()))
                .andExpect(jsonPath("$.title").value("探す投稿"))
                .andExpect(jsonPath("$.authorUsername").value("mod_author"))
                .andExpect(jsonPath("$.hidden").value(false));
        f.as(f.admin, get("/api/admin/moderation/users").param("username", "no_such_user")).andExpect(status().isNotFound());
    }

    @Test
    void 一般の利用者は404_ログインしていなければ401() throws Exception {
        Post post = f.createPost(f.author, "権限の投稿");

        f.as(f.viewer, post("/api/admin/moderation/posts/" + post.getId() + "/hide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"SPAM\"}")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/moderation/posts/" + post.getId() + "/hide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"SPAM\"}")).andExpect(status().isUnauthorized());
        f.as(f.viewer, get("/api/admin/moderation/hidden-posts")).andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("SELECT hidden_at FROM posts WHERE id = ?", Object.class, post.getId()))
                .isNull();
    }

    // ---- 部品 ----

    private ResultActions hide(long postId, String reason, String memo) throws Exception {
        return f.as(f.admin, post("/api/admin/moderation/posts/" + postId + "/hide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\", \"memo\": " + (memo == null ? "null" : "\"" + memo + "\"") + "}"));
    }

    private ResultActions unhide(long postId) throws Exception {
        return f.as(f.admin, post("/api/admin/moderation/posts/" + postId + "/unhide")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private ResultActions suspend(long userId, String reason, Integer days, String memo) throws Exception {
        return f.as(f.admin, post("/api/admin/moderation/users/" + userId + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\", \"days\": " + days + ", \"memo\": "
                        + (memo == null ? "null" : "\"" + memo + "\"") + "}"));
    }

    private ResultActions unsuspend(long userId) throws Exception {
        return f.as(f.admin, post("/api/admin/moderation/users/" + userId + "/unsuspend")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private void report(String targetType, long targetId, String reason) throws Exception {
        f.as(f.viewer, post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"" + targetType + "\", \"targetId\": " + targetId + ", \"reason\": \""
                                + reason + "\"}"))
                .andExpect(status().isCreated());
    }

    private long openCaseId(String targetType, long targetId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM report_cases WHERE target_type = ? AND target_id = ? AND status = 'OPEN'",
                Long.class, targetType, targetId);
    }

    @SuppressWarnings("unused")
    private List<String> logActions() {
        return jdbcTemplate.queryForList("SELECT action FROM admin_operation_logs ORDER BY id", String.class);
    }
}
