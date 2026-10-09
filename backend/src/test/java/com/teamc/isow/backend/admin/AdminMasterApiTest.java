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
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * マスタ管理の API（ファッションの種類・骨格タイプ・パーソナルカラー。/api/admin/masters/{kind}。docs/admin.md「マスタの管理」）の確認。
 * 3つは同じ作りのため、多くのテストを3種類それぞれで確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminMasterApiTest {

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

    /** ログの保存をわざと失敗させるために使う（通知・起動時の昇格のテストと同じやり方） */
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

    // ---- 一覧 ----

    @ParameterizedTest
    @CsvSource({"fashion-categories, fashion_categories", "body-types, body_types", "personal-colors, personal_colors"})
    void 一覧は無効なものも含めて並び順に返す(String kind, String table) throws Exception {
        Long firstId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM " + table, Long.class);
        jdbcTemplate.update("UPDATE " + table + " SET is_active = false WHERE id = ?", firstId);
        int total = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);

        asAdmin(get("/api/admin/masters/" + kind))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(total))
                .andExpect(jsonPath("$.items[0].id").value(firstId))
                .andExpect(jsonPath("$.items[0].active").value(false))
                .andExpect(jsonPath("$.items[1].active").value(true))
                .andExpect(jsonPath("$.items[0].displayOrder").isNumber());
    }

    // ---- 追加 ----

    @ParameterizedTest
    @CsvSource({
        "fashion-categories, fashion_categories, FASHION_CATEGORY",
        "body-types, body_types, BODY_TYPE",
        "personal-colors, personal_colors, PERSONAL_COLOR",
    })
    void 追加すると末尾に並び_ログが残る(String kind, String table, String targetType) throws Exception {
        int maxOrder = jdbcTemplate.queryForObject("SELECT MAX(display_order) FROM " + table, Integer.class);

        String body = add(kind, "　管理テスト　フェミニン ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("管理テスト フェミニン"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        int order = jdbcTemplate.queryForObject("SELECT display_order FROM " + table + " WHERE id = ?", Integer.class, id);
        assertThat(order).isGreaterThan(maxOrder);
        Map<String, Object> log = onlyLog();
        assertThat(log.get("action")).isEqualTo("MASTER_CREATED");
        assertThat(log.get("target_type")).isEqualTo(targetType);
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat(((Number) log.get("operator_id")).longValue()).isEqualTo(admin.getId());
        assertThat((String) log.get("detail")).contains("管理テスト フェミニン");
    }

    @Test
    void 追加したものは利用者側の選択肢に出る() throws Exception {
        add("personal-colors", "管理テスト色").andExpect(status().isCreated());

        mockMvc.perform(get("/api/masters").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.personalColors[*].name", hasItem("管理テスト色")));
    }

    @ParameterizedTest
    @CsvSource({"管理テストＭＯＤＥ, 管理テストmode", "管理テスト　ﾓｰﾄﾞ, 管理テスト モード", "管理テスト  モード  , 管理テスト モード"})
    void 表記ゆれだけが違う名前は重複として追加しない(String first, String second) throws Exception {
        add("fashion-categories", first).andExpect(status().isCreated());

        add("fashion-categories", second)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fashion_categories WHERE name LIKE '管理テスト%'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 初期データと同じ名前は重複_無効なものと同じ名前も重複() throws Exception {
        String seeded = jdbcTemplate.queryForObject("SELECT name FROM body_types ORDER BY id LIMIT 1", String.class);
        jdbcTemplate.update("UPDATE body_types SET is_active = false WHERE name = ?", seeded);

        add("body-types", seeded)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void 名前が空や長すぎる場合は400() throws Exception {
        add("body-types", "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        add("body-types", "管理テスト" + "あ".repeat(50))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        assertThat(logCount()).isZero();
    }

    // ---- 無効化・有効に戻す ----

    @ParameterizedTest
    @CsvSource({
        "fashion-categories, fashion_categories, fashionCategories, FASHION_CATEGORY",
        "body-types, body_types, bodyTypes, BODY_TYPE",
        "personal-colors, personal_colors, personalColors, PERSONAL_COLOR",
    })
    void 無効にすると利用者側の選択肢から消え_有効に戻すとまた出る_どちらもログが残る(
            String kind, String table, String mastersField, String targetType) throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT MIN(id) FROM " + table, Long.class);

        asAdmin(post("/api/admin/masters/" + kind + "/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/masters").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$." + mastersField + "[*].id", not(hasItem(id.intValue()))));

        asAdmin(post("/api/admin/masters/" + kind + "/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/masters").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$." + mastersField + "[*].id", hasItem(id.intValue())));

        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT action, target_type, target_id FROM admin_operation_logs ORDER BY id");
        assertThat(logs).extracting(log -> log.get("action")).containsExactly("MASTER_DEACTIVATED", "MASTER_ACTIVATED");
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.get("target_type")).isEqualTo(targetType);
            assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        });
    }

    @Test
    void すでに無効なものを無効にしても_何も変わらずログも残さない() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT MIN(id) FROM body_types", Long.class);
        asAdmin(post("/api/admin/masters/body-types/" + id + "/deactivate")).andExpect(status().isOk());

        asAdmin(post("/api/admin/masters/body-types/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void 無効にしても_そのマスタを使っている投稿とプロフィールの表示は壊れない() throws Exception {
        Long colorId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM personal_colors", Long.class);
        String colorName = jdbcTemplate.queryForObject("SELECT name FROM personal_colors WHERE id = ?", String.class, colorId);
        jdbcTemplate.update("UPDATE users SET personal_color_id = ? WHERE id = ?", colorId, user.getId());
        var category = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
        Post post = postRepository.save(new Post(user, "題名", category, null, "説明", null,
                List.of("http://localhost/uploads/a.jpg"),
                List.of(tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0))));

        asAdmin(post("/api/admin/masters/personal-colors/" + colorId + "/deactivate")).andExpect(status().isOk());
        asAdmin(post("/api/admin/masters/fashion-categories/" + category.getId() + "/deactivate")).andExpect(status().isOk());

        // 設定済みのプロフィール・既存の投稿は、無効にしたマスタの名前のまま表示できる
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalColor.id").value(colorId))
                .andExpect(jsonPath("$.personalColor.name").value(colorName));
        mockMvc.perform(get("/api/posts/" + post.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fashionCategory.name").value(category.getName()));
    }

    // ---- ログを残せなければ操作も取り消す ----

    @Test
    void ログを残せなければ追加も取り消される() throws Exception {
        failLogSave();

        assertFailsBySavingLog(() -> add("fashion-categories", "管理テスト取り消し"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fashion_categories WHERE name = '管理テスト取り消し'", Integer.class)).isZero();
    }

    @Test
    void ログを残せなければ無効化も取り消される() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT MIN(id) FROM personal_colors", Long.class);
        failLogSave();

        assertFailsBySavingLog(() -> asAdmin(post("/api/admin/masters/personal-colors/" + id + "/deactivate")));

        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM personal_colors WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void ログを残せなければ有効に戻す操作も取り消される() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT MIN(id) FROM body_types", Long.class);
        jdbcTemplate.update("UPDATE body_types SET is_active = false WHERE id = ?", id);
        failLogSave();

        assertFailsBySavingLog(() -> asAdmin(post("/api/admin/masters/body-types/" + id + "/activate")));

        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM body_types WHERE id = ?", Boolean.class, id))
                .isFalse();
    }

    // ---- 権限・存在しないもの ----

    @Test
    void 一般の利用者は404_トークンなしは401() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT MIN(id) FROM body_types", Long.class);
        String[][] requests = {
            {"GET", "/api/admin/masters/body-types"},
            {"POST", "/api/admin/masters/body-types"},
            {"POST", "/api/admin/masters/body-types/" + id + "/deactivate"},
            {"POST", "/api/admin/masters/body-types/" + id + "/activate"},
        };
        for (String[] request : requests) {
            var builder = "GET".equals(request[0]) ? get(request[1]) : post(request[1])
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"管理テスト権限\"}");
            mockMvc.perform(builder.header("Authorization", bearer(user))).andExpect(status().isNotFound());
            var anonymous = "GET".equals(request[0]) ? get(request[1]) : post(request[1])
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"管理テスト権限\"}");
            mockMvc.perform(anonymous).andExpect(status().isUnauthorized());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM body_types WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(logCount()).isZero();
    }

    @Test
    void 存在しない種類やIDは404() throws Exception {
        asAdmin(get("/api/admin/masters/genders")).andExpect(status().isNotFound());
        asAdmin(post("/api/admin/masters/body-types/999999/deactivate")).andExpect(status().isNotFound());
    }

    // ---- 部品 ----

    private ResultActions add(String kind, String name) throws Exception {
        return asAdmin(post("/api/admin/masters/" + kind)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private ResultActions asAdmin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
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

    private Map<String, Object> onlyLog() {
        return jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
    }

    private int logCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class);
    }
}
