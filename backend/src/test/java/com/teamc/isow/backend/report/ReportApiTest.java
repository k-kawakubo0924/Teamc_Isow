package com.teamc.isow.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 利用者の通報（POST /api/reports。docs/admin.md「通報」）の確認。
 * 対象ごとにまとめること、通報した時点で証拠（文字・写真）をコピーすること、入力の確認、通報される人に伝わらないことを確かめる
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.evidence.local.dir}")
    private Path evidenceDir;

    @Value("${app.image.local.dir}")
    private Path uploadDir;

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

    // ---- 投稿の通報と証拠のコピー ----

    @Test
    void 投稿を通報すると201で受け付け_対象ごとの通報と1人分の通報ができる() throws Exception {
        long postId = f.createPost(f.target, "通報される投稿", 1);

        f.report(f.reporter, "POST", postId, "SPAM", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        long caseId = f.openCaseId("POST", postId);
        assertThat(f.count("SELECT count(*) FROM reports WHERE case_id = ? AND reporter_id = ? AND reason = 'SPAM'",
                caseId, f.reporter.getId())).isEqualTo(1);
        assertThat(f.count("SELECT report_count FROM report_cases WHERE id = ?", caseId)).isEqualTo(1);
        assertThat(f.count("SELECT target_user_id FROM report_cases WHERE id = ?", caseId)).isEqualTo(f.target.getId());
    }

    @Test
    void 通報した時点で投稿の文字をコピーし_写真は公開フォルダとは別の場所に複製する() throws Exception {
        long postId = f.createPost(f.target, "証拠になる投稿", 2);
        List<String> originalUrls = f.postImageUrls(postId);

        f.report(f.reporter, "POST", postId, "UNAUTHORIZED_PHOTO", null).andExpect(status().isCreated());

        long caseId = f.openCaseId("POST", postId);
        String evidence = jdbcTemplate.queryForObject("SELECT evidence_json FROM report_cases WHERE id = ?",
                String.class, caseId);
        assertThat(evidence).contains("証拠になる投稿", "証拠になる投稿の説明", "コート：古着屋", "古着", "rpt_target",
                "https://example.com/item");
        List<String> evidenceFiles = f.evidenceFileNames(caseId);
        assertThat(evidenceFiles).hasSize(2);
        for (int i = 0; i < 2; i++) {
            String name = evidenceFiles.get(i);
            // 中身は元の写真と同じ（再圧縮しない）。置き場所は証拠のフォルダだけで、公開フォルダには置かない
            Path copy = evidenceDir.resolve(name);
            assertThat(copy).exists();
            assertThat(Files.readAllBytes(copy)).isEqualTo(Files.readAllBytes(
                    uploadDir.resolve(originalUrls.get(i).substring(originalUrls.get(i).lastIndexOf('/') + 1))));
            assertThat(uploadDir.resolve(name)).doesNotExist();
            // 元の URL のファイル名をそのまま使わない（公開の URL から推測できないように）
            assertThat(originalUrls.get(i)).doesNotEndWith(name);
        }
    }

    @Test
    void ユーザーを通報すると名前とプロフィール画像をコピーする() throws Exception {
        jdbcTemplate.update("UPDATE users SET display_name = ? WHERE id = ?", "表示名テスト", f.target.getId());

        f.report(f.reporter, "USER", f.target.getId(), "IMPERSONATION", "有名人のなりすましです")
                .andExpect(status().isCreated());

        long caseId = f.openCaseId("USER", f.target.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT evidence_json FROM report_cases WHERE id = ?", String.class, caseId))
                .contains("rpt_target", "表示名テスト");
        assertThat(jdbcTemplate.queryForObject("SELECT comment FROM reports WHERE case_id = ?", String.class, caseId))
                .isEqualTo("有名人のなりすましです");
    }

    @Test
    void DMのメッセージを通報すると_通報したメッセージと前後5件ずつを通報した時点でコピーする() throws Exception {
        long conversationId = f.activeConversation(f.reporter, f.target);
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            ids.add(f.send(i % 2 == 0 ? f.reporter : f.target, conversationId, "メッセージ" + i, i == 7));
        }
        long reported = ids.get(8); // 9件目（相手 f.target のメッセージ）

        f.report(f.reporter, "MESSAGE", reported, "HARASSMENT", null).andExpect(status().isCreated());
        // 通報の後に送ったメッセージは含めない
        f.send(f.target, conversationId, "通報の後のメッセージ", false);

        long caseId = f.openCaseId("MESSAGE", reported);
        String evidence = jdbcTemplate.queryForObject("SELECT evidence_json FROM report_cases WHERE id = ?",
                String.class, caseId);
        for (int i = 4; i <= 14; i++) {
            assertThat(evidence).contains("\"メッセージ" + i + "\"");
        }
        assertThat(evidence).doesNotContain("\"メッセージ3\"", "\"メッセージ15\"", "通報の後のメッセージ");
        // 7件目の画像も証拠としてコピーする
        assertThat(f.evidenceFileNames(caseId)).hasSize(1);
    }

    // ---- 同じ対象への複数の通報 ----

    @Test
    void 同じ人が同じ対象をもう一度通報しても1件のまま_別の人の通報は同じ対象にまとまる() throws Exception {
        long postId = f.createPost(f.target, "何度も通報される投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());

        f.report(f.reporter, "POST", postId, "HARASSMENT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_REPORTED"));
        f.report(f.reporter2, "POST", postId, "HARASSMENT", null).andExpect(status().isCreated());

        long caseId = f.openCaseId("POST", postId);
        assertThat(f.count("SELECT count(*) FROM report_cases WHERE target_type = 'POST' AND target_id = ?", postId))
                .isEqualTo(1);
        assertThat(f.count("SELECT count(*) FROM reports WHERE case_id = ?", caseId)).isEqualTo(2);
        assertThat(f.count("SELECT report_count FROM report_cases WHERE id = ?", caseId)).isEqualTo(2);
        // 証拠のコピーは対象ごとに1回だけ
        assertThat(f.evidenceFileNames(caseId)).hasSize(1);
    }

    @Test
    void 対応を終えた対象をまた通報すると新しい通報として受け付け_証拠もコピーし直す() throws Exception {
        long postId = f.createPost(f.target, "また通報される投稿", 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        long firstCase = f.openCaseId("POST", postId);
        jdbcTemplate.update("UPDATE report_cases SET status = 'RESOLVED', open = NULL, closed_at = CURRENT_TIMESTAMP "
                + "WHERE id = ?", firstCase);

        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());

        long secondCase = f.openCaseId("POST", postId);
        assertThat(secondCase).isNotEqualTo(firstCase);
        assertThat(f.evidenceFileNames(secondCase)).hasSize(1);
    }

    // ---- 入力の確認 ----

    @Test
    void 理由は対象に合ったものだけ_その他は詳しい内容が必須_詳しい内容は500文字まで() throws Exception {
        long postId = f.createPost(f.target, "入力確認の投稿", 1);

        f.report(f.reporter, "POST", postId, "IMPERSONATION", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
        f.report(f.reporter, "USER", f.target.getId(), "UNAUTHORIZED_PHOTO", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
        f.report(f.reporter, "POST", postId, "NO_SUCH_REASON", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
        f.report(f.reporter, "POST", postId, "OTHER", "  ")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.comment").exists());
        f.report(f.reporter, "POST", postId, "SPAM", "あ".repeat(501))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.comment").exists());
        f.report(f.reporter, "NO_SUCH_TYPE", postId, "SPAM", null).andExpect(status().isBadRequest());

        f.report(f.reporter, "POST", postId, "OTHER", "あ".repeat(500)).andExpect(status().isCreated());
        assertThat(f.count("SELECT count(*) FROM reports")).isEqualTo(1);
    }

    @Test
    void 自分自身_自分の投稿_自分のメッセージは通報できない() throws Exception {
        long myPost = f.createPost(f.reporter, "自分の投稿", 1);
        long conversationId = f.activeConversation(f.reporter, f.target);
        long myMessage = f.send(f.reporter, conversationId, "自分のメッセージ", false);

        f.report(f.reporter, "USER", f.reporter.getId(), "SPAM", null).andExpect(status().isBadRequest());
        f.report(f.reporter, "POST", myPost, "SPAM", null).andExpect(status().isBadRequest());
        f.report(f.reporter, "MESSAGE", myMessage, "SPAM", null).andExpect(status().isBadRequest());

        assertThat(f.count("SELECT count(*) FROM report_cases")).isZero();
    }

    @Test
    void 存在しない対象と_当事者でない会話のメッセージは404() throws Exception {
        long conversationId = f.activeConversation(f.target, f.reporter2);
        long othersMessage = f.send(f.target, conversationId, "他人どうしのメッセージ", false);

        f.report(f.reporter, "POST", 999999999L, "SPAM", null).andExpect(status().isNotFound());
        f.report(f.reporter, "USER", 999999999L, "SPAM", null).andExpect(status().isNotFound());
        f.report(f.reporter, "MESSAGE", othersMessage, "SPAM", null).andExpect(status().isNotFound());

        assertThat(f.count("SELECT count(*) FROM report_cases")).isZero();
    }

    @Test
    void 相手にブロックされていれば存在しないものと同じ404_自分がブロックした相手は通報できる() throws Exception {
        long postOfTarget = f.createPost(f.target, "ブロックの間柄の投稿", 1);
        f.as(f.target, post("/api/users/" + f.reporter.getId() + "/block")).andExpect(status().isNoContent());

        f.report(f.reporter, "POST", postOfTarget, "SPAM", null).andExpect(status().isNotFound());
        f.report(f.reporter, "USER", f.target.getId(), "SPAM", null).andExpect(status().isNotFound());
        // ブロックした側（f.target）は、ブロックした相手（f.reporter）を通報できる
        f.report(f.target, "USER", f.reporter.getId(), "HARASSMENT", null).andExpect(status().isCreated());
    }

    @Test
    void 通報は1人1日30件まで() throws Exception {
        long postId = f.createPost(f.target, "上限確認の投稿", 1);
        for (int i = 0; i < 30; i++) {
            jdbcTemplate.update("INSERT INTO report_cases (target_type, target_id, target_user_id, status, open, "
                    + "report_count, first_reported_at, last_reported_at) VALUES ('USER', ?, ?, 'OPEN', TRUE, 1, "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", 900000 + i, f.target.getId());
            jdbcTemplate.update("INSERT INTO reports (case_id, reporter_id, reason, reported_at) VALUES ("
                    + "(SELECT MAX(id) FROM report_cases), ?, 'SPAM', CURRENT_TIMESTAMP)", f.reporter.getId());
        }

        f.report(f.reporter, "POST", postId, "SPAM", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("通報の上限に達しました。時間をおいて再度お試しください。"));
        // 別の人は通報できる
        f.report(f.reporter2, "POST", postId, "SPAM", null).andExpect(status().isCreated());
    }

    // ---- 通報される人に伝わらない ----

    @Test
    void 通報しても通報された人に通知は届かず_自動でブロックもしない() throws Exception {
        long postId = f.createPost(f.target, "通知確認の投稿", 1);
        long notificationsBefore = f.count("SELECT count(*) FROM notifications");

        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());

        assertThat(f.count("SELECT count(*) FROM notifications")).isEqualTo(notificationsBefore);
        assertThat(f.count("SELECT count(*) FROM blocks")).isZero();
    }

    @Test
    void 未ログインは401() throws Exception {
        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"USER\", \"targetId\": " + f.target.getId() + ", \"reason\": \"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }
}
