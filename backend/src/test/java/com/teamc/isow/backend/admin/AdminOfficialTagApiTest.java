package com.teamc.isow.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 公式タグの管理 API（/api/admin/official-tags。docs/admin.md「マスタの管理」）の確認。
 * タグは手入力のタグと同じ表で持つため、他のマスタとは別に作っている。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminOfficialTagApiTest {

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

    @MockitoSpyBean
    private AdminOperationLogRepository logRepository;

    private User admin;
    private User user;

    @BeforeEach
    void setUp() {
        admin = AdminTestSupport.createAdmin(userRepository, jdbcTemplate);
        user = AdminTestSupport.createUser(userRepository);
    }

    @AfterEach
    void tearDown() {
        AdminTestSupport.cleanUp(jdbcTemplate);
    }

    @Test
    void 一覧は公式タグだけを無効なものも含めて返し_手入力のタグは含めない() throws Exception {
        Tag userTag = tagRepository.save(Tag.userInput("管理テスト手入力"));
        Long firstOfficial = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE is_official = true ORDER BY display_order, id LIMIT 1", Long.class);
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE id = ?", firstOfficial);
        int officialCount = jdbcTemplate.queryForObject("SELECT count(*) FROM tags WHERE is_official = true", Integer.class);

        asAdmin(get("/api/admin/official-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(officialCount))
                .andExpect(jsonPath("$.items[0].id").value(firstOfficial))
                .andExpect(jsonPath("$.items[0].active").value(false))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(userTag.getId().intValue()))));
    }

    @Test
    void 新しい名前は公式タグとして末尾に追加し_ログが残る() throws Exception {
        int maxOrder = jdbcTemplate.queryForObject(
                "SELECT MAX(display_order) FROM tags WHERE is_official = true", Integer.class);

        String body = add("＃管理テスト新規")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.madeOfficial").value(false))
                .andExpect(jsonPath("$.tag.name").value("管理テスト新規"))
                .andExpect(jsonPath("$.tag.active").value(true))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(body, "$.tag.id")).longValue();
        Tag saved = tagRepository.findById(id).orElseThrow();
        assertThat(saved.isOfficial()).isTrue();
        assertThat(saved.getDisplayOrder()).isGreaterThan(maxOrder);
        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("action")).isEqualTo("MASTER_CREATED");
        assertThat(log.get("target_type")).isEqualTo("TAG");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        mockMvc.perform(get("/api/masters").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.tags[*].name", hasItem("管理テスト新規")));
    }

    @Test
    void 手入力のタグと同じ名前なら新しく作らずに公式にし_投稿との関連付けは残る() throws Exception {
        Tag userTag = tagRepository.save(Tag.userInput("管理テストY2K"));
        Post post = postRepository.save(new Post(user, "題名",
                fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0), null, "説明", null,
                List.of("http://localhost/uploads/a.jpg"), List.of(userTag)));
        int tagCount = jdbcTemplate.queryForObject("SELECT count(*) FROM tags", Integer.class);

        add("管理テストｙ２ｋ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.madeOfficial").value(true))
                .andExpect(jsonPath("$.tag.id").value(userTag.getId()))
                // 表示名は最初に登録されたときの表記のまま
                .andExpect(jsonPath("$.tag.name").value("管理テストY2K"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tags", Integer.class)).isEqualTo(tagCount);
        Tag promoted = tagRepository.findById(userTag.getId()).orElseThrow();
        assertThat(promoted.isOfficial()).isTrue();
        assertThat(promoted.getDisplayOrder()).isNotNull();
        assertThat(jdbcTemplate.queryForList("SELECT tag_id FROM post_tags WHERE post_id = ?", Long.class, post.getId()))
                .containsExactly(userTag.getId());
        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("action")).isEqualTo("TAG_MADE_OFFICIAL");
        assertThat(log.get("target_type")).isEqualTo("TAG");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(userTag.getId());
        mockMvc.perform(get("/api/masters").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.tags[*].id", hasItem(userTag.getId().intValue())));
        mockMvc.perform(get("/api/posts/" + post.getId()).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.tags[0].id").value(userTag.getId()))
                .andExpect(jsonPath("$.tags[0].official").value(true));
    }

    @Test
    void 無効にされた手入力のタグと同じ名前なら_公式にして有効に戻す() throws Exception {
        Tag userTag = tagRepository.save(Tag.userInput("管理テスト無効"));
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE id = ?", userTag.getId());

        add("管理テスト無効").andExpect(status().isOk()).andExpect(jsonPath("$.madeOfficial").value(true))
                .andExpect(jsonPath("$.tag.active").value(true));
    }

    @Test
    void すでにある公式タグと同じ名前は400_無効な公式タグと同じ名前も400() throws Exception {
        String officialName = jdbcTemplate.queryForObject(
                "SELECT name FROM tags WHERE is_official = true ORDER BY id LIMIT 1", String.class);
        add(officialName.toUpperCase()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());

        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE name = ?", officialName);
        add(officialName).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class)).isZero();
    }

    @Test
    void 名前が空や長すぎる場合は400() throws Exception {
        add("＃ ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        add("管理テスト" + "あ".repeat(30)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void 無効にすると選択肢から消え_有効に戻すとまた出る_どちらもログが残る() throws Exception {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE is_official = true ORDER BY id LIMIT 1", Long.class);

        asAdmin(post("/api/admin/official-tags/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/masters").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.tags[*].id", not(hasItem(id.intValue()))));

        asAdmin(post("/api/admin/official-tags/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/masters").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.tags[*].id", hasItem(id.intValue())));

        assertThat(jdbcTemplate.queryForList("SELECT action FROM admin_operation_logs ORDER BY id", String.class))
                .containsExactly("MASTER_DEACTIVATED", "MASTER_ACTIVATED");
    }

    @Test
    void 手入力のタグや存在しないIDを無効にしようとすると404() throws Exception {
        Tag userTag = tagRepository.save(Tag.userInput("管理テスト手入力"));

        asAdmin(post("/api/admin/official-tags/" + userTag.getId() + "/deactivate")).andExpect(status().isNotFound());
        asAdmin(post("/api/admin/official-tags/999999/activate")).andExpect(status().isNotFound());
        assertThat(tagRepository.findById(userTag.getId()).orElseThrow().isActive()).isTrue();
    }

    // ---- ログを残せなければ操作も取り消す ----

    @Test
    void ログを残せなければ追加も取り消される() throws Exception {
        failLogSave();

        assertFailsBySavingLog(() -> add("管理テスト取り消し"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tags WHERE name = '管理テスト取り消し'", Integer.class)).isZero();
    }

    @Test
    void ログを残せなければ公式にする操作も取り消される() throws Exception {
        Tag userTag = tagRepository.save(Tag.userInput("管理テスト昇格"));
        failLogSave();

        assertFailsBySavingLog(() -> add("管理テスト昇格"));

        Tag reloaded = tagRepository.findById(userTag.getId()).orElseThrow();
        assertThat(reloaded.isOfficial()).isFalse();
        assertThat(reloaded.getDisplayOrder()).isNull();
    }

    @Test
    void ログを残せなければ無効化も有効に戻す操作も取り消される() throws Exception {
        Long activeId = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE is_official = true ORDER BY id LIMIT 1", Long.class);
        Long inactiveId = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE is_official = true ORDER BY id DESC LIMIT 1", Long.class);
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE id = ?", inactiveId);
        failLogSave();

        assertFailsBySavingLog(() -> asAdmin(post("/api/admin/official-tags/" + activeId + "/deactivate")));
        assertFailsBySavingLog(() -> asAdmin(post("/api/admin/official-tags/" + inactiveId + "/activate")));

        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM tags WHERE id = ?", Boolean.class, activeId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM tags WHERE id = ?", Boolean.class, inactiveId))
                .isFalse();
    }

    // ---- 権限 ----

    @Test
    void 一般の利用者は404_トークンなしは401() throws Exception {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE is_official = true ORDER BY id LIMIT 1", Long.class);
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/admin/official-tags"),
                post("/api/admin/official-tags").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"管理テスト権限\"}"),
                post("/api/admin/official-tags/" + id + "/deactivate"),
                post("/api/admin/official-tags/" + id + "/activate"));
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request.header("Authorization", bearer(user))).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/admin/official-tags")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/official-tags").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"管理テスト権限\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/official-tags/" + id + "/deactivate")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/official-tags/" + id + "/activate")).andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tags WHERE name = '管理テスト権限'", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class)).isZero();
    }

    // ---- 部品 ----

    private ResultActions add(String name) throws Exception {
        return asAdmin(post("/api/admin/official-tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(admin)));
    }

    private String bearer(User target) {
        return AdminTestSupport.bearer(jwtEncoder, target);
    }

    /**
     * ログの保存の失敗が操作の処理に伝わること（画面には 500 で返る。MockMvc ではエラー画面を通らず、例外のまま投げ直される）
     */
    private void assertFailsBySavingLog(ThrowingCallable request) {
        assertThatThrownBy(request).hasRootCauseMessage("ログの保存に失敗（テスト）");
    }

    private void failLogSave() {
        doThrow(new IllegalStateException("ログの保存に失敗（テスト）")).when(logRepository).save(any());
    }
}
