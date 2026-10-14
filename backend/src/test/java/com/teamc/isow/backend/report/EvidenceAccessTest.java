package com.teamc.isow.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 証拠の写真の配信（docs/admin.md「通報」の「証拠の写真の配信」）の確認。この機能で一番重要な部分。
 *
 * <ul>
 *   <li>証拠の写真は /uploads/** では配信しない（ファイル名が分かっても取得できない）</li>
 *   <li>管理者向けの API だけが、管理者であることを確かめてから中身を返す。未ログインは 401、一般の利用者は 404</li>
 *   <li>利用者が投稿を削除した後も、一般の経路からは取得できず、管理者は見られる</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvidenceAccessTest {

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
    private long postId;
    private long caseId;
    private List<String> originalUrls;
    private List<String> evidenceNames;
    private List<Long> evidenceIds;

    @BeforeEach
    void setUp() throws Exception {
        f = new ReportFixture(mockMvc, jwtEncoder, userRepository, jdbcTemplate);
        f.createUsers();
        postId = f.createPost(f.target, "証拠の配信の確認", 2);
        originalUrls = f.postImageUrls(postId);
        f.report(f.reporter, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        caseId = f.openCaseId("POST", postId);
        evidenceNames = f.evidenceFileNames(caseId);
        evidenceIds = f.evidenceFileIds(caseId);
    }

    @AfterEach
    void tearDown() {
        f.cleanUp();
    }

    @Test
    void 管理者は管理者向けAPIから証拠の写真を取得でき_中身は元の写真と同じ_ブラウザに残さない() throws Exception {
        byte[] original = Files.readAllBytes(uploadDir.resolve(fileName(originalUrls.get(0))));

        byte[] served = f.getAs(f.admin, evidencePath(caseId, evidenceIds.get(0)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(served).isEqualTo(original);
    }

    @Test
    void 証拠の写真の管理者向けAPIは_ログインしていない人は401_一般の利用者は通報した人もされた人も404() throws Exception {
        String path = evidencePath(caseId, evidenceIds.get(0));

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        f.getAs(f.reporter, path).andExpect(status().isNotFound());
        f.getAs(f.target, path).andExpect(status().isNotFound());
        f.getAs(f.reporter2, path).andExpect(status().isNotFound());
    }

    @Test
    void 証拠の写真はファイル名が分かっても_uploadsからは誰も取得できない() throws Exception {
        // 確かめる意味があるか：元の投稿の写真は /uploads から取得できている（配信の仕組みはテストでも動いている）
        mockMvc.perform(get("/uploads/" + fileName(originalUrls.get(0)))).andExpect(status().isOk());

        for (String name : evidenceNames) {
            mockMvc.perform(get("/uploads/" + name)).andExpect(status().isNotFound());
            f.getAs(f.reporter, "/uploads/" + name).andExpect(status().isNotFound());
            f.getAs(f.admin, "/uploads/" + name).andExpect(status().isNotFound());
        }
    }

    @Test
    void 利用者が投稿を削除した後も_証拠の写真は一般の経路からは取得できず_管理者だけが見られる() throws Exception {
        f.as(f.target, delete("/api/posts/" + postId)).andExpect(status().isNoContent());

        // 元の写真は投稿と一緒に消える
        mockMvc.perform(get("/uploads/" + fileName(originalUrls.get(0)))).andExpect(status().isNotFound());
        assertThat(uploadDir.resolve(fileName(originalUrls.get(0)))).doesNotExist();
        // 証拠の写真は残っているが、一般の経路からは取得できない
        for (int i = 0; i < evidenceNames.size(); i++) {
            assertThat(evidenceDir.resolve(evidenceNames.get(i))).exists();
            mockMvc.perform(get("/uploads/" + evidenceNames.get(i))).andExpect(status().isNotFound());
            mockMvc.perform(get(evidencePath(caseId, evidenceIds.get(i)))).andExpect(status().isUnauthorized());
            f.getAs(f.target, evidencePath(caseId, evidenceIds.get(i))).andExpect(status().isNotFound());
            f.getAs(f.admin, evidencePath(caseId, evidenceIds.get(i))).andExpect(status().isOk());
        }
    }

    @Test
    void 別の通報の証拠の写真をIDの組み合わせで取ろうとしても404() throws Exception {
        long otherPost = f.createPost(f.target, "別の投稿", 1);
        f.report(f.reporter, "POST", otherPost, "SPAM", null).andExpect(status().isCreated());
        long otherCase = f.openCaseId("POST", otherPost);

        f.getAs(f.admin, evidencePath(otherCase, evidenceIds.get(0))).andExpect(status().isNotFound());
        f.getAs(f.admin, evidencePath(caseId, 999999999L)).andExpect(status().isNotFound());
    }

    @Test
    void 証拠の保存先は公開フォルダの外にあり_中に置く設定では起動しない() {
        Path uploads = uploadDir.toAbsolutePath().normalize();
        Path evidence = evidenceDir.toAbsolutePath().normalize();
        assertThat(evidence.startsWith(uploads)).isFalse();
        assertThat(uploads.startsWith(evidence)).isFalse();

        assertThatThrownBy(() -> new LocalEvidenceStorage(uploads.resolve("evidence"), uploads))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalEvidenceStorage(uploads, uploads)).isInstanceOf(IllegalStateException.class);
    }

    private static String evidencePath(long caseId, long fileId) {
        return "/api/admin/reports/" + caseId + "/evidence-files/" + fileId;
    }

    private static String fileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
