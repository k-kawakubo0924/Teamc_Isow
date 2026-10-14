package com.teamc.isow.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.UserRepository;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 証拠の保存期間（対応を終えてから180日）を過ぎたものを消す処理（EvidencePurger。docs/admin.md「通報」）の確認。
 * このプロジェクトで最初の定期実行のため、時刻を引数で差し替えて、境目・消す範囲・失敗したときの動きを確かめる
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class EvidencePurgerTest {

    /** 基準の時刻（テストの中の「今」）。この時刻を EvidencePurger に渡す */
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 3, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EvidencePurger purger;

    @MockitoSpyBean
    private EvidenceStorage evidenceStorage;

    @Value("${app.evidence.local.dir}")
    private Path evidenceDir;

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

    @Test
    void 対応を終えてから180日を過ぎたものだけ消え_過ぎていないもの_未対応のものは残る() throws Exception {
        long expired = closedCase("期限を過ぎた投稿", NOW.minusDays(180).minusMinutes(1));
        long justBefore = closedCase("まだ期限前の投稿", NOW.minusDays(180).plusMinutes(1));
        long open = openCase("未対応の投稿");
        jdbcTemplate.update("UPDATE report_cases SET first_reported_at = ? WHERE id = ?",
                Timestamp.valueOf(NOW.minusDays(1000)), open);
        List<String> expiredFiles = f.evidenceFileNames(expired);
        List<String> keptFiles = f.evidenceFileNames(justBefore);
        List<String> openFiles = f.evidenceFileNames(open);

        EvidencePurger.Result result = purger.purgeExpired(NOW);

        assertThat(result.purgedCases()).isEqualTo(1);
        assertThat(result.failedFiles()).isZero();
        expiredFiles.forEach(name -> assertThat(evidenceDir.resolve(name)).doesNotExist());
        assertThat(f.evidenceFileNames(expired)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT evidence_json FROM report_cases WHERE id = ?", String.class, expired))
                .isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT evidence_purged_at FROM report_cases WHERE id = ?",
                Timestamp.class, expired).toLocalDateTime()).isEqualTo(NOW);
        // 通報そのもの（理由・状態・日時）は残す
        assertThat(f.count("SELECT count(*) FROM reports WHERE case_id = ?", expired)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report_cases WHERE id = ?", String.class, expired))
                .isEqualTo("RESOLVED");

        for (long kept : new long[] {justBefore, open}) {
            assertThat(jdbcTemplate.queryForObject("SELECT evidence_json FROM report_cases WHERE id = ?", String.class, kept))
                    .isNotNull();
            assertThat(jdbcTemplate.queryForObject("SELECT evidence_purged_at FROM report_cases WHERE id = ?",
                    Timestamp.class, kept)).isNull();
        }
        keptFiles.forEach(name -> assertThat(evidenceDir.resolve(name)).exists());
        openFiles.forEach(name -> assertThat(evidenceDir.resolve(name)).exists());
        assertThat(f.evidenceFileNames(justBefore)).hasSize(1);
    }

    @Test
    void 消すのは証拠として記録した正確なファイル名だけで_名前が似たファイルやほかのファイルは消さない() throws Exception {
        long expired = closedCase("消える投稿", NOW.minusDays(200));
        String name = f.evidenceFileNames(expired).get(0);
        String base = name.substring(0, name.lastIndexOf('.'));
        List<Path> decoys = List.of(evidenceDir.resolve(name + ".bak"), evidenceDir.resolve(base + ".jpg"),
                evidenceDir.resolve("x" + name), evidenceDir.resolve("unrelated.png"));
        for (Path decoy : decoys) {
            Files.write(decoy, new byte[] {1, 2, 3});
        }
        try {
            purger.purgeExpired(NOW);

            assertThat(evidenceDir.resolve(name)).doesNotExist();
            decoys.forEach(decoy -> assertThat(decoy).exists());
        } finally {
            for (Path decoy : decoys) {
                Files.deleteIfExists(decoy);
            }
        }
    }

    @Test
    void ファイルを消せなかった通報は_消せなかったファイルの記録を残して次回に回し_ほかの通報は続けて消しログに残す(
            CapturedOutput output) throws Exception {
        long failing = closedCase("消せない投稿", NOW.minusDays(200));
        long other = closedCase("消せる投稿", NOW.minusDays(200));
        String failingName = f.evidenceFileNames(failing).get(0);
        doThrow(new UncheckedIOException("削除に失敗（テスト）", new java.io.IOException()))
                .when(evidenceStorage).delete(eq(failingName));

        EvidencePurger.Result result = purger.purgeExpired(NOW);

        assertThat(result.purgedCases()).isEqualTo(1);
        assertThat(result.failedFiles()).isEqualTo(1);
        // 消せなかった通報は、記録を残して「消した」にしない（次の実行で消し直す）
        assertThat(f.evidenceFileNames(failing)).containsExactly(failingName);
        assertThat(evidenceDir.resolve(failingName)).exists();
        assertThat(jdbcTemplate.queryForObject("SELECT evidence_purged_at FROM report_cases WHERE id = ?",
                Timestamp.class, failing)).isNull();
        // ほかの通報は消す
        assertThat(f.evidenceFileNames(other)).isEmpty();
        // 失敗はログに残す（通報の ID とファイル名。投稿の内容・ユーザー名は書かない）
        assertThat(output.getAll()).contains("証拠のファイルを削除できませんでした", "caseId=" + failing, failingName);
        assertThat(output.getAll()).doesNotContain("消せない投稿");

        // 次の実行で消し直せる
        doCallRealMethod().when(evidenceStorage).delete(eq(failingName));
        EvidencePurger.Result retry = purger.purgeExpired(NOW.plusDays(1));
        assertThat(retry.purgedCases()).isEqualTo(1);
        assertThat(evidenceDir.resolve(failingName)).doesNotExist();
        assertThat(f.evidenceFileNames(failing)).isEmpty();
    }

    @Test
    void 何度実行しても同じ結果で_消した後の通報は管理者の詳細で証拠を削除済みと出す() throws Exception {
        long expired = closedCase("2回実行する投稿", NOW.minusDays(365));

        purger.purgeExpired(NOW);
        EvidencePurger.Result second = purger.purgeExpired(NOW);

        assertThat(second.purgedCases()).isZero();
        assertThat(second.failedFiles()).isZero();
        f.getAs(f.admin, "/api/admin/reports/" + expired)
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.evidencePurged")
                        .value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.evidenceFiles")
                        .isEmpty());
    }

    // ---- 部品 ----

    /** 写真1枚の投稿を通報し、対応済みにして、終了日時を closedAt にする */
    private long closedCase(String title, LocalDateTime closedAt) throws Exception {
        long caseId = openCase(title);
        jdbcTemplate.update("UPDATE report_cases SET status = 'RESOLVED', open = NULL, closed_at = ? WHERE id = ?",
                Timestamp.valueOf(closedAt), caseId);
        return caseId;
    }

    private long openCase(String title) throws Exception {
        long postId = f.createPost(f.target, title, 1);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        return f.openCaseId("POST", postId);
    }
}
