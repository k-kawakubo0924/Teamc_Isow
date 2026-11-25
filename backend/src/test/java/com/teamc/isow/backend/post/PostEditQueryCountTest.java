package com.teamc.isow.backend.post;

import static com.teamc.isow.backend.post.PostApiTestSupport.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 投稿の編集で、タグの数に比例して SQL が増えないこと（N+1 問題）の確認。
 * タグが1個の投稿と10個の投稿で、タグはそのままに文字だけを編集し、SQL の本数が同じであることを確かめる
 * （タグを付け外しした場合は、付け外ししたタグの数だけ post_tags の追加・削除が出る。投稿の作成と同じ）
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class PostEditQueryCountTest {

    private static final List<String> TEN_TAGS = List.of("古着", "プチプラ", "韓国ファッション", "モノトーン",
            "オフィスカジュアル", "デートコーデ", "低身長コーデ", "高身長コーデ", "春コーデ", "夏コーデ");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String token;
    private long fashionCategoryId;

    @BeforeEach
    void setUp() {
        User me = userRepository.save(new User(PostApiTestSupport.EMAIL, "09000000182", "hash", "post_edit_count"));
        token = PostApiTestSupport.token(jwtEncoder, me.getId());
        fashionCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM fashion_categories WHERE name = 'きれいめ'", Long.class);
    }

    @AfterEach
    void tearDown() {
        PostApiTestSupport.cleanUp(jdbcTemplate);
    }

    @Test
    void タグを変えずに文字だけを編集するとき_タグの数に関係なくSQLの本数が一定() throws Exception {
        long oneTagPost = create(List.of("古着"));
        long tenTagsPost = create(TEN_TAGS);

        long one = countSql(oneTagPost, List.of("古着"));
        long ten = countSql(tenTagsPost, TEN_TAGS);

        System.out.printf("### 編集の SQL の本数（タグ1個 → 10個）: %d → %d%n", one, ten);
        assertThat(ten).isEqualTo(one);
    }

    private long create(List<String> tags) throws Exception {
        MockMultipartHttpServletRequestBuilder request = validRequest(token, fashionCategoryId, "tags", tags.get(0));
        tags.stream().skip(1).forEach(tag -> request.param("tags", tag));
        String body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long countSql(long postId, List<String> tags) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "題名を直した");
        body.put("fashionCategoryId", fashionCategoryId);
        body.put("tags", tags);
        body.put("description", "説明を直した");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(put("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }
}
