package com.teamc.isow.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.admin.AdminOperationLogRepository;
import com.teamc.isow.backend.user.UserRepository;
import java.util.ArrayList;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 管理者の通報の確認（/api/admin/reports/**。docs/admin.md「通報」）。
 * 一覧（対象ごとにまとめる）・詳細（証拠・通報した人・今の状態）・状態の記録とログ・DM を見たことのログを確かめる。
 * 措置（非公開化・削除・利用停止）はまだないため、状態を記録するだけ
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AdminOperationLogRepository logRepository;

    private ReportFixture f;

    @BeforeEach
    void setUp() {
        f = new ReportFixture(mockMvc, jwtEncoder, userRepository, jdbcTemplate);
        f.createUsers();
    }

    @AfterEach
    void tearDown() {
        f.cleanUp();
    }

    // ---- 一覧 ----

    @Test
    void 一覧は対象ごとに1行で_通報の数と理由の内訳を出し_既定は未対応だけを新しい順() throws Exception {
        long postId = f.createPost(f.target, "一覧の確認の投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        f.report(f.reporter2, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        f.report(f.reporter, "USER", f.target.getId(), "HARASSMENT", null).andExpect(status().isCreated());
        long userCase = f.openCaseId("USER", f.target.getId());
        long postCase = f.openCaseId("POST", postId);
        jdbcTemplate.update("UPDATE report_cases SET last_reported_at = DATEADD('HOUR', -1, last_reported_at) WHERE id = ?",
                postCase);

        f.getAs(f.admin, "/api/admin/reports")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].caseId", contains((int) userCase, (int) postCase)))
                .andExpect(jsonPath("$.items[1].targetType").value("POST"))
                .andExpect(jsonPath("$.items[1].summary").value("一覧の確認の投稿"))
                .andExpect(jsonPath("$.items[1].reportCount").value(2))
                .andExpect(jsonPath("$.items[1].reasons[0].reason").value("SPAM"))
                .andExpect(jsonPath("$.items[1].reasons[0].count").value(2))
                .andExpect(jsonPath("$.items[1].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].summary").value("@rpt_target"));
        f.getAs(f.admin, "/api/admin/reports?targetType=POST")
                .andExpect(jsonPath("$.items[*].caseId", contains((int) postCase)));
        f.getAs(f.admin, "/api/admin/reports?status=RESOLVED").andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 一覧ではDMのメッセージの中身を出さない_見たことを記録する詳細でだけ出す() throws Exception {
        long conversationId = f.activeConversation(f.reporter, f.target);
        long message = f.send(f.target, conversationId, "一覧には出さない本文", false);
        f.report(f.reporter, "MESSAGE", message, "HARASSMENT", null).andExpect(status().isCreated());

        String body = f.getAs(f.admin, "/api/admin/reports").andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("一覧には出さない本文");
        assertThat(body).contains("@rpt_target のメッセージ");
        assertThat(f.count("SELECT count(*) FROM admin_operation_logs")).isZero();
    }

    // ---- 詳細 ----

    @Test
    void 投稿の詳細は_通報した時点の証拠_通報した人と理由_今の状態を返し_一般の利用者には404() throws Exception {
        long postId = f.createPost(f.target, "詳細の確認の投稿", 2);
        f.report(f.reporter, "POST", postId, "UNAUTHORIZED_PHOTO", "私の写真です").andExpect(status().isCreated());
        long caseId = f.openCaseId("POST", postId);

        f.getAs(f.admin, "/api/admin/reports/" + caseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.post.title").value("詳細の確認の投稿"))
                .andExpect(jsonPath("$.evidence.post.authorUsername").value("rpt_target"))
                .andExpect(jsonPath("$.evidence.post.tags[0]").value("古着"))
                .andExpect(jsonPath("$.evidenceFiles", hasSize(2)))
                .andExpect(jsonPath("$.evidencePurged").value(false))
                .andExpect(jsonPath("$.currentState").value("EXISTS"))
                .andExpect(jsonPath("$.reports[0].reporterUsername").value("rpt_reporter"))
                .andExpect(jsonPath("$.reports[0].reason").value("UNAUTHORIZED_PHOTO"))
                .andExpect(jsonPath("$.reports[0].comment").value("私の写真です"));
        f.getAs(f.reporter, "/api/admin/reports/" + caseId).andExpect(status().isNotFound());
        f.getAs(f.target, "/api/admin/reports").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/reports/" + caseId)).andExpect(status().isUnauthorized());
        // 投稿の詳細を見ただけではログを残さない
        assertThat(f.count("SELECT count(*) FROM admin_operation_logs")).isZero();
    }

    @Test
    void 通報の後に編集_削除された投稿は_今の状態でそれが分かり_証拠は通報した時点のまま() throws Exception {
        long postId = f.createPost(f.target, "後から変わる投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        long caseId = f.openCaseId("POST", postId);
        String tag = "古着";

        f.as(f.target, put("/api/posts/" + postId).contentType(MediaType.APPLICATION_JSON).content(
                "{\"title\": \"書き換えた題名\", \"fashionCategoryId\": " + jdbcTemplate.queryForObject(
                        "SELECT fashion_category_id FROM posts WHERE id = ?", Long.class, postId)
                        + ", \"tags\": [\"" + tag + "\"], \"description\": \"書き換えた説明\"}"))
                .andExpect(status().isOk());
        f.getAs(f.admin, "/api/admin/reports/" + caseId)
                .andExpect(jsonPath("$.currentState").value("EDITED"))
                .andExpect(jsonPath("$.evidence.post.title").value("後から変わる投稿"));

        f.as(f.target, delete("/api/posts/" + postId)).andExpect(status().isNoContent());
        f.getAs(f.admin, "/api/admin/reports/" + caseId)
                .andExpect(jsonPath("$.currentState").value("DELETED"))
                .andExpect(jsonPath("$.evidence.post.title").value("後から変わる投稿"))
                .andExpect(jsonPath("$.evidenceFiles", hasSize(1)));
    }

    @Test
    void DMの詳細は通報したメッセージと前後5件ずつの11件だけで_どれが通報されたかが分かり_見たことをログに残す() throws Exception {
        long conversationId = f.activeConversation(f.reporter, f.target);
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            ids.add(f.send(i % 2 == 0 ? f.reporter : f.target, conversationId, "本文" + i, false));
        }
        long reported = ids.get(10); // 11件目（相手のメッセージ）
        f.report(f.reporter, "MESSAGE", reported, "HARASSMENT", null).andExpect(status().isCreated());
        long caseId = f.openCaseId("MESSAGE", reported);

        String body = f.getAs(f.admin, "/api/admin/reports/" + caseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.messages", hasSize(11)))
                .andExpect(jsonPath("$.evidence.messages[0].body").value("本文6"))
                .andExpect(jsonPath("$.evidence.messages[10].body").value("本文16"))
                .andExpect(jsonPath("$.evidence.messages[5].messageId").value(reported))
                .andExpect(jsonPath("$.evidence.messages[5].reported").value(true))
                .andExpect(jsonPath("$.evidence.messages[5].senderUsername").value("rpt_target"))
                .andExpect(jsonPath("$.evidence.messages[4].reported").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"本文5\"", "\"本文17\"");

        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("action")).isEqualTo("REPORT_DM_VIEWED");
        assertThat(log.get("target_type")).isEqualTo("REPORT");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(caseId);
        assertThat(((Number) log.get("operator_id")).longValue()).isEqualTo(f.admin.getId());
        // ログの内容に、メッセージの本文・ユーザー名を書かない
        assertThat((String) log.get("detail")).doesNotContain("本文", "rpt_");
    }

    @Test
    void DMを見たことのログを残せなければ_DMの中身も返さない() throws Exception {
        long conversationId = f.activeConversation(f.reporter, f.target);
        long message = f.send(f.target, conversationId, "記録できなければ見せない本文", false);
        f.report(f.reporter, "MESSAGE", message, "HARASSMENT", null).andExpect(status().isCreated());
        long caseId = f.openCaseId("MESSAGE", message);
        doThrow(new IllegalStateException("ログの保存に失敗（テスト）")).when(logRepository).save(any());

        assertThatThrownBy(() -> f.getAs(f.admin, "/api/admin/reports/" + caseId))
                .hasRootCauseMessage("ログの保存に失敗（テスト）");
    }

    // ---- 状態の記録 ----

    @Test
    void 対応済みにすると状態_終了日時_対応した管理者_メモが残り_ログには状態だけを書く() throws Exception {
        long postId = f.createPost(f.target, "対応済みにする投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", "通報した人の書いた内容").andExpect(status().isCreated());
        long caseId = f.openCaseId("POST", postId);

        changeStatus(caseId, "RESOLVED", "投稿者に連絡済み（メモ）")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.handledByUsername").value("rpt_admin"))
                .andExpect(jsonPath("$.adminMemo").value("投稿者に連絡済み（メモ）"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        Map<String, Object> saved = jdbcTemplate.queryForMap("SELECT * FROM report_cases WHERE id = ?", caseId);
        assertThat(saved.get("status")).isEqualTo("RESOLVED");
        assertThat(saved.get("open")).isNull();
        assertThat(saved.get("closed_at")).isNotNull();
        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("action")).isEqualTo("REPORT_RESOLVED");
        assertThat(log.get("target_type")).isEqualTo("REPORT");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(caseId);
        assertThat((String) log.get("detail")).doesNotContain("メモ", "通報した人の書いた内容", "rpt_");
    }

    @Test
    void 却下と未対応に戻すもでき_それぞれログに残る() throws Exception {
        long postId = f.createPost(f.target, "却下する投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        long caseId = f.openCaseId("POST", postId);

        changeStatus(caseId, "DISMISSED", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISMISSED"));
        changeStatus(caseId, "OPEN", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closedAt").value(nullValue()));

        assertThat(jdbcTemplate.queryForList("SELECT action FROM admin_operation_logs ORDER BY id", String.class))
                .containsExactly("REPORT_DISMISSED", "REPORT_REOPENED");
    }

    @Test
    void 同じ対象に新しい未対応の通報があるときは_古い通報を未対応に戻せない() throws Exception {
        long postId = f.createPost(f.target, "戻せない投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        long firstCase = f.openCaseId("POST", postId);
        changeStatus(firstCase, "RESOLVED", null).andExpect(status().isOk());
        f.report(f.reporter2, "POST", postId, "SPAM", null).andExpect(status().isCreated());

        changeStatus(firstCase, "OPEN", null).andExpect(status().isConflict());
    }

    @Test
    void 状態とメモの入力の誤りは400_存在しない通報は404() throws Exception {
        long postId = f.createPost(f.target, "入力確認の投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        long caseId = f.openCaseId("POST", postId);

        changeStatus(caseId, "NO_SUCH_STATUS", null).andExpect(status().isBadRequest());
        changeStatus(caseId, "RESOLVED", "あ".repeat(1001)).andExpect(status().isBadRequest());
        changeStatus(999999999L, "RESOLVED", null).andExpect(status().isNotFound());
        assertThat(f.count("SELECT count(*) FROM admin_operation_logs")).isZero();
    }

    @Test
    void ログを残せなければ状態も変わらない() throws Exception {
        long postId = f.createPost(f.target, "ログ失敗の投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        long caseId = f.openCaseId("POST", postId);
        doThrow(new IllegalStateException("ログの保存に失敗（テスト）")).when(logRepository).save(any());

        assertThatThrownBy(() -> changeStatus(caseId, "RESOLVED", null)).hasRootCauseMessage("ログの保存に失敗（テスト）");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("OPEN");
    }

    @Test
    void 一般の利用者は状態を変えられない() throws Exception {
        long postId = f.createPost(f.target, "権限の投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        long caseId = f.openCaseId("POST", postId);

        f.as(f.reporter, post("/api/admin/reports/" + caseId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("OPEN");
    }

    private ResultActions changeStatus(long caseId, String status, String memo) throws Exception {
        String memoJson = memo == null ? "null" : "\"" + memo + "\"";
        return f.as(f.admin, post("/api/admin/reports/" + caseId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\", \"memo\": " + memoJson + "}"));
    }
}
