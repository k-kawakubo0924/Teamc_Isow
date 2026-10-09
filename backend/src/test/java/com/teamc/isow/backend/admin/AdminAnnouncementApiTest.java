package com.teamc.isow.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** お知らせの発行・一覧の API（/api/admin/announcements。docs/admin.md「お知らせの発行」）の確認 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAnnouncementApiTest {

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

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private User admin;
    private User user;

    @BeforeEach
    void setUp() {
        admin = AdminTestSupport.createAdmin(userRepository, jdbcTemplate);
        user = AdminTestSupport.createUser(userRepository);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM announcements");
        AdminTestSupport.cleanUp(jdbcTemplate);
    }

    @Test
    void 発行すると題名_本文_発行日時_発行者が保存され_ログが残る() throws Exception {
        String body = publish("メンテナンスのお知らせ", "10月10日 2:00〜4:00 にメンテナンスを行います。\nご不便をおかけします。")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("メンテナンスのお知らせ"))
                .andExpect(jsonPath("$.body").value("10月10日 2:00〜4:00 にメンテナンスを行います。\nご不便をおかけします。"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.publisherId").value(admin.getId()))
                .andExpect(jsonPath("$.publisherUsername").value("admin_api_admin"))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        Map<String, Object> saved = jdbcTemplate.queryForMap("SELECT * FROM announcements WHERE id = ?", id);
        assertThat(saved.get("title")).isEqualTo("メンテナンスのお知らせ");
        assertThat(saved.get("published_at")).isNotNull();
        assertThat(((Number) saved.get("publisher_id")).longValue()).isEqualTo(admin.getId());
        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(log.get("action")).isEqualTo("ANNOUNCEMENT_PUBLISHED");
        assertThat(log.get("target_type")).isEqualTo("ANNOUNCEMENT");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(id);
        assertThat(((Number) log.get("operator_id")).longValue()).isEqualTo(admin.getId());
        assertThat((String) log.get("detail")).contains("メンテナンスのお知らせ");
    }

    @Test
    void 本文は文字のまま保存し_HTMLとして扱わない() throws Exception {
        String html = "<b>太字</b><script>alert(1)</script>&amp;";

        publish("題名<i>", html)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("題名<i>"))
                .andExpect(jsonPath("$.body").value(html));

        assertThat(jdbcTemplate.queryForObject("SELECT body FROM announcements", String.class)).isEqualTo(html);
    }

    @Test
    void 前後の空白は除き_改行はLFにそろえる() throws Exception {
        publish("  題名  ", "\r\n 1行目\r\n2行目\r3行目 \n")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("題名"))
                .andExpect(jsonPath("$.body").value("1行目\n2行目\n3行目"));
    }

    @Test
    void 題名と本文は必須() throws Exception {
        publish(" ", "本文").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").exists());
        publish("題名", "\n \n").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.body").exists());
        publish(null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.body").exists());
        assertNothingSaved();
    }

    @Test
    void 題名は100文字まで_本文は2000文字まで() throws Exception {
        publish("あ".repeat(100), "い".repeat(2000)).andExpect(status().isCreated());
        // 改行は1文字と数える（\r\n で送られても2文字にしない）
        publish("題名", "い".repeat(999) + "\r\n" + "い".repeat(1000)).andExpect(status().isCreated());

        publish("あ".repeat(101), "本文").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").exists());
        publish("題名", "い".repeat(2001)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.body").exists());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM announcements", Integer.class)).isEqualTo(2);
    }

    @Test
    void 一覧は新しい順に返し_ページで区切る() throws Exception {
        for (int i = 1; i <= 3; i++) {
            publish("お知らせ" + i, "本文" + i).andExpect(status().isCreated());
        }

        asAdmin(get("/api/admin/announcements").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("お知らせ3"))
                .andExpect(jsonPath("$.items[1].title").value("お知らせ2"))
                .andExpect(jsonPath("$.items[0].body").value("本文3"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        asAdmin(get("/api/admin/announcements").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("お知らせ1"))
                .andExpect(jsonPath("$.hasNext").value(false));
        // 範囲外の size はエラーにせず丸める（操作のログの一覧と同じ）
        asAdmin(get("/api/admin/announcements").param("page", "-1").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void 一覧を見るだけではログを残さない() throws Exception {
        asAdmin(get("/api/admin/announcements")).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class)).isZero();
    }

    @Test
    void ログを残せなければ発行も取り消される() throws Exception {
        doThrow(new IllegalStateException("ログの保存に失敗（テスト）")).when(logRepository).save(any());

        assertThatThrownBy(() -> publish("取り消し", "本文")).hasRootCauseMessage("ログの保存に失敗（テスト）");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM announcements", Integer.class)).isZero();
    }

    @Test
    void 一般の利用者は404_トークンなしは401() throws Exception {
        String content = json("権限", "本文");
        mockMvc.perform(get("/api/admin/announcements").header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/announcements").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/announcements")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isUnauthorized());

        assertNothingSaved();
    }

    // ---- 部品 ----

    private ResultActions publish(String title, String body) throws Exception {
        return asAdmin(post("/api/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(title, body)));
    }

    private String json(String title, String body) throws Exception {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("title", title);
        request.put("body", body);
        return jsonMapper.writeValueAsString(request);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(admin)));
    }

    private String bearer(User target) {
        return AdminTestSupport.bearer(jwtEncoder, target);
    }

    private void assertNothingSaved() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM announcements", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class)).isZero();
    }
}
