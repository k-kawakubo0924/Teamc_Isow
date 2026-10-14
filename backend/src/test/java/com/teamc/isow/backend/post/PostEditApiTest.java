package com.teamc.isow.backend.post;

import static com.teamc.isow.backend.post.PostApiTestSupport.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 投稿の編集（PUT /api/posts/{id}。docs/post.md「投稿の編集」）の確認。
 * 投稿は画面と同じ API で作ってから編集する。写真は編集できない（文字の情報だけ）
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostEditApiTest {

    private static final String OTHER_EMAIL = "post-edit-other@example.com";
    /** 自分の投稿に付ける手入力のタグ */
    private static final String MY_TAG = "編集確認用のタグ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String myToken;
    private String otherToken;
    /** きれいめ（自分の投稿に設定済み）・カジュアル（未設定） */
    private long kireimeId;
    private long casualId;

    /** 自分の投稿（写真2枚・タグは公式タグ「古着」と手入力のタグ。相手がいいねしている） */
    private long myPostId;
    private List<String> myPostUrls;
    /** 相手の投稿（タグは公式タグ「プチプラ」） */
    private long otherPostId;

    @BeforeEach
    void setUp() throws Exception {
        User me = userRepository.save(new User(PostApiTestSupport.EMAIL, "09000000180", "hash", "post_edit_me"));
        User other = userRepository.save(new User(OTHER_EMAIL, "09000000181", "hash", "post_edit_other"));
        myToken = PostApiTestSupport.token(jwtEncoder, me.getId());
        otherToken = PostApiTestSupport.token(jwtEncoder, other.getId());
        kireimeId = categoryId("きれいめ");
        casualId = categoryId("カジュアル");

        MockMultipartHttpServletRequestBuilder mine = validRequest(myToken, kireimeId, "wornItems", "コート：古着屋");
        mine.file(new org.springframework.mock.web.MockMultipartFile(
                "images", "2.jpg", "image/jpeg", PostApiTestSupport.image(20, 20, "jpg")));
        mine.param("tags", MY_TAG);
        String myPost = create(mine);
        myPostId = ((Number) JsonPath.read(myPost, "$.id")).longValue();
        myPostUrls = JsonPath.read(myPost, "$.imageUrls");

        String otherPost = create(validRequest(otherToken, kireimeId, "tags", "プチプラ"));
        otherPostId = ((Number) JsonPath.read(otherPost, "$.id")).longValue();

        mockMvc.perform(post("/api/posts/" + myPostId + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().is2xxSuccessful());
    }

    @AfterEach
    void tearDown() {
        PostApiTestSupport.cleanUp(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", OTHER_EMAIL);
    }

    // ---- 自分の投稿の編集 ----

    @Test
    void 文字の情報を編集すると200で編集後の内容を返し_編集日時が入り_投稿日時と写真は変わらない() throws Exception {
        Object createdAt = jdbcTemplate.queryForObject("SELECT created_at FROM posts WHERE id = ?", Object.class, myPostId);

        edit(myToken, myPostId, body("冬のコート", casualId, List.of("古着", "冬コーデ", "新しい手入力"),
                "コート：古着屋\nマフラー：ニット", "説明を書き直しました", "https://example.com/coat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(myPostId))
                .andExpect(jsonPath("$.title").value("冬のコート"))
                .andExpect(jsonPath("$.fashionCategory.id").value(casualId))
                .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder("古着", "冬コーデ", "新しい手入力")))
                .andExpect(jsonPath("$.wornItems").value("コート：古着屋\nマフラー：ニット"))
                .andExpect(jsonPath("$.description").value("説明を書き直しました"))
                .andExpect(jsonPath("$.referenceUrl").value("https://example.com/coat"))
                .andExpect(jsonPath("$.imageUrls").value(myPostUrls))
                .andExpect(jsonPath("$.editedAt").isNotEmpty())
                .andExpect(jsonPath("$.ownedByMe").value(true))
                // いいねは残る
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(jdbcTemplate.queryForObject("SELECT edited_at FROM posts WHERE id = ?", Object.class, myPostId))
                .isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT created_at FROM posts WHERE id = ?", Object.class, myPostId))
                .isEqualTo(createdAt);
        assertThat(count("SELECT count(*) FROM post_images WHERE post_id = ?", myPostId)).isEqualTo(2);
        // 外したタグ（手入力のタグ）は投稿から外れるが、タグそのものは残る
        assertThat(count("SELECT count(*) FROM post_tags WHERE post_id = ?", myPostId)).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM tags WHERE name = ?", MY_TAG)).isEqualTo(1);
    }

    @Test
    void 編集していない投稿の編集日時はnullで_編集すると詳細でも返す() throws Exception {
        detail(myPostId).andExpect(jsonPath("$.editedAt").value(nullValue()));

        edit(myToken, myPostId, validBody()).andExpect(status().isOk());

        detail(myPostId).andExpect(jsonPath("$.editedAt").isNotEmpty());
    }

    @Test
    void タグだけを変えても編集日時が入る() throws Exception {
        edit(myToken, myPostId, body("秋の羽織りもの", kireimeId, List.of("古着"), "コート：古着屋",
                "丈が長めのコートなので、下は細めにまとめています。", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder("古着")))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
    }

    @Test
    void 写真の項目を送っても写真は変わらない() throws Exception {
        Map<String, Object> request = validBody();
        request.put("imageUrls", List.of("http://localhost/uploads/other.jpg"));
        request.put("images", List.of("http://localhost/uploads/other.jpg"));

        edit(myToken, myPostId, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrls").value(myPostUrls));
    }

    @Test
    void 前後の空白を除き_改行はLFにそろえ_任意の項目は空なら未設定にする() throws Exception {
        edit(myToken, myPostId, body("  題名  ", kireimeId, List.of("古着"), " \r\n ", "1行目\r\n2行目\r3行目 ", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("題名"))
                .andExpect(jsonPath("$.wornItems").value(nullValue()))
                .andExpect(jsonPath("$.description").value("1行目\n2行目\n3行目"))
                .andExpect(jsonPath("$.referenceUrl").value(nullValue()));
    }

    @Test
    void 入力チェックは作成と同じで_誤りがあれば400で何も変えない() throws Exception {
        assertRejected(body(" ", kireimeId, List.of("古着"), null, "説明", null), "title");
        assertRejected(body("あ".repeat(101), kireimeId, List.of("古着"), null, "説明", null), "title");
        assertRejected(body("題名", null, List.of("古着"), null, "説明", null), "fashionCategoryId");
        assertRejected(body("題名", 999999L, List.of("古着"), null, "説明", null), "fashionCategoryId");
        assertRejected(body("題名", kireimeId, List.of(), null, "説明", null), "tags");
        assertRejected(body("題名", kireimeId, List.of("あ".repeat(31)), null, "説明", null), "tags");
        assertRejected(body("題名", kireimeId,
                List.of("t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10", "t11"), null, "説明", null), "tags");
        assertRejected(body("題名", kireimeId, List.of("古着"), "あ".repeat(1001), "説明", null), "wornItems");
        assertRejected(body("題名", kireimeId, List.of("古着"), null, " ", null), "description");
        assertRejected(body("題名", kireimeId, List.of("古着"), null, "い".repeat(2001), null), "description");
        assertRejected(body("題名", kireimeId, List.of("古着"), null, "説明", "javascript:alert(1)"), "referenceUrl");
    }

    @Test
    void 編集しても通知は作らない() throws Exception {
        long before = count("SELECT count(*) FROM notifications", null);

        edit(myToken, myPostId, validBody()).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM notifications", null)).isEqualTo(before);
    }

    // ---- 他人の投稿・存在しない投稿・未ログイン ----

    @Test
    void 他人の投稿は403で何も変えない() throws Exception {
        edit(myToken, otherPostId, validBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("この投稿は編集できません。"));

        assertThat(jdbcTemplate.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, otherPostId))
                .isEqualTo("秋の羽織りもの");
        assertThat(jdbcTemplate.queryForObject("SELECT edited_at FROM posts WHERE id = ?", Object.class, otherPostId))
                .isNull();
    }

    @Test
    void 存在しない投稿は404_未ログインは401() throws Exception {
        edit(myToken, 999999999L, validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("投稿が見つかりません。"));
        mockMvc.perform(put("/api/posts/" + myPostId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validBody())))
                .andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject("SELECT edited_at FROM posts WHERE id = ?", Object.class, myPostId))
                .isNull();
    }

    // ---- 無効にされたマスタ（docs/admin.md の宿題。設定済みかどうかは DB の値で判定する） ----

    @Test
    void 設定済みのファッションの種類は無効になってもそのまま保存できる() throws Exception {
        jdbcTemplate.update("UPDATE fashion_categories SET is_active = false WHERE id = ?", kireimeId);

        edit(myToken, myPostId, body("題名を直した", kireimeId, List.of("古着", MY_TAG), null, "説明", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fashionCategory.id").value(kireimeId));
    }

    @Test
    void 設定されていない無効なファッションの種類には変えられない() throws Exception {
        jdbcTemplate.update("UPDATE fashion_categories SET is_active = false WHERE id = ?", casualId);

        assertRejected(body("題名", casualId, List.of("古着"), null, "説明", null), "fashionCategoryId");
    }

    @Test
    void 設定済みのタグは無効になってもそのまま保存できる() throws Exception {
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE name IN (?, ?)", "古着", MY_TAG);

        edit(myToken, myPostId, body("題名を直した", kireimeId, List.of("古着", MY_TAG, "冬コーデ"), null, "説明", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder("古着", MY_TAG, "冬コーデ")));
    }

    @Test
    void 設定済みの無効なタグは表記ゆれで送っても同じタグとして残せる() throws Exception {
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE name = ?", "古着");

        edit(myToken, myPostId, body("題名", kireimeId, List.of("#古着"), null, "説明", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder("古着")));
    }

    @Test
    void この投稿に付いていない無効なタグは_ほかの投稿に付いていても付けられない() throws Exception {
        // 相手の投稿に付いている「プチプラ」を無効にする。自分の投稿には付いていない
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE name = ?", "プチプラ");

        assertRejected(body("題名", kireimeId, List.of("古着", "プチプラ"), null, "説明", null), "tags");
    }

    @Test
    void 一度外した無効なタグは付け直せない() throws Exception {
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE name = ?", "古着");
        edit(myToken, myPostId, body("題名", kireimeId, List.of(MY_TAG), null, "説明", null)).andExpect(status().isOk());

        assertRejected(body("題名", kireimeId, List.of(MY_TAG, "古着"), null, "説明", null), "tags");
    }

    // ---- 部品 ----

    private void assertRejected(Map<String, Object> request, String field) throws Exception {
        String titleBefore = jdbcTemplate.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, myPostId);
        long tagsBefore = count("SELECT count(*) FROM post_tags WHERE post_id = ?", myPostId);
        Object editedAtBefore = jdbcTemplate.queryForObject(
                "SELECT edited_at FROM posts WHERE id = ?", Object.class, myPostId);

        edit(myToken, myPostId, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());

        assertThat(jdbcTemplate.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, myPostId))
                .isEqualTo(titleBefore);
        assertThat(count("SELECT count(*) FROM post_tags WHERE post_id = ?", myPostId)).isEqualTo(tagsBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT edited_at FROM posts WHERE id = ?", Object.class, myPostId))
                .isEqualTo(editedAtBefore);
    }

    private Map<String, Object> validBody() {
        return body("冬のコート", kireimeId, List.of("古着", MY_TAG), null, "説明を書き直しました", null);
    }

    private static Map<String, Object> body(String title, Long fashionCategoryId, List<String> tags, String wornItems,
            String description, String referenceUrl) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("title", title);
        request.put("fashionCategoryId", fashionCategoryId);
        request.put("tags", tags);
        request.put("wornItems", wornItems);
        request.put("description", description);
        request.put("referenceUrl", referenceUrl);
        return request;
    }

    private ResultActions edit(String token, long postId, Map<String, Object> request) throws Exception {
        return mockMvc.perform(put("/api/posts/" + postId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }

    private ResultActions detail(long postId) throws Exception {
        return mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + myToken))
                .andExpect(status().isOk());
    }

    private String create(MockMultipartHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private long categoryId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM fashion_categories WHERE name = ?", Long.class, name);
    }

    private long count(String sql, Object arg) {
        Long value = arg == null
                ? jdbcTemplate.queryForObject(sql, Long.class)
                : jdbcTemplate.queryForObject(sql, Long.class, arg);
        return value == null ? 0 : value;
    }
}
