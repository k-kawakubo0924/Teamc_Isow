package com.teamc.isow.backend.post;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 投稿の取得（GET /api/posts/{id}、GET /api/posts/me）の確認。
 * 投稿は Repository で直接作る（画像ファイルは使わないため、URL だけを持たせる）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostQueryApiTest {

    private static final String OTHER_EMAIL = "post-query-other@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User other;
    private String token;
    private FashionCategory kireime;
    private Tag furugi;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(PostApiTestSupport.EMAIL, "09000000000", "hash", "post_query_me"));
        other = userRepository.save(new User(OTHER_EMAIL, "09000000001", "hash", "post_query_other"));
        token = PostApiTestSupport.token(jwtEncoder, me.getId());
        kireime = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
        furugi = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
    }

    @AfterEach
    void tearDown() {
        PostApiTestSupport.cleanUp(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", OTHER_EMAIL);
    }

    // ---- 詳細 ----

    @Test
    void 詳細には画像_ファッションの種類_タグ_投稿者のユーザー名が含まれる() throws Exception {
        Tag outer = tagRepository.save(Tag.userInput("アウター"));
        // タグは付けた順ではなく ID 順に並ぶ
        Post post = postRepository.save(new Post(me, "秋の羽織りもの", kireime, "アウター：古着屋で購入", "説明",
                "https://example.com/item", List.of("http://localhost/uploads/a.jpg", "http://localhost/uploads/b.png"),
                List.of(outer, furugi)));

        getWithToken("/api/posts/" + post.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(post.getId()))
                .andExpect(jsonPath("$.title").value("秋の羽織りもの"))
                .andExpect(jsonPath("$.fashionCategory.id").value(kireime.getId()))
                .andExpect(jsonPath("$.fashionCategory.name").value(kireime.getName()))
                .andExpect(jsonPath("$.tags[*].name").value(contains("古着", "アウター")))
                .andExpect(jsonPath("$.tags[*].official").value(contains(true, false)))
                .andExpect(jsonPath("$.wornItems").value("アウター：古着屋で購入"))
                .andExpect(jsonPath("$.description").value("説明"))
                .andExpect(jsonPath("$.referenceUrl").value("https://example.com/item"))
                .andExpect(jsonPath("$.imageUrls").value(contains("http://localhost/uploads/a.jpg", "http://localhost/uploads/b.png")))
                .andExpect(jsonPath("$.author.id").value(me.getId()))
                .andExpect(jsonPath("$.author.username").value("post_query_me"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void 他のユーザーの投稿の詳細も取得できる() throws Exception {
        Post post = save(other, "他の人の投稿");

        getWithToken("/api/posts/" + post.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.username").value("post_query_other"));
    }

    @Test
    void 存在しない投稿は404() throws Exception {
        getWithToken("/api/posts/999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("投稿が見つかりません。"));
        getWithToken("/api/posts/abc").andExpect(status().isNotFound());
    }

    // ---- 自分の投稿一覧 ----

    @Test
    void 自分の投稿だけが新しい順に返る() throws Exception {
        Post oldest = save(me, "1番目");
        Post newest = save(me, "2番目");
        Post middle = save(me, "3番目");
        save(other, "他の人の投稿");
        // 作成した順（ID 順）とは違う投稿日時にして、投稿日時で並ぶことを確かめる
        setCreatedAt(oldest, LocalDateTime.of(2026, 1, 1, 0, 0));
        setCreatedAt(newest, LocalDateTime.of(2026, 3, 1, 0, 0));
        setCreatedAt(middle, LocalDateTime.of(2026, 2, 1, 0, 0));

        getWithToken("/api/posts/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[*].id").value(contains(id(newest), id(middle), id(oldest))))
                .andExpect(jsonPath("$.posts[0].imageUrls", hasSize(1)))
                .andExpect(jsonPath("$.posts[0].fashionCategory.name").value(kireime.getName()))
                .andExpect(jsonPath("$.posts[0].tags[0].name").value("古着"))
                .andExpect(jsonPath("$.posts[0].author.username").value("post_query_me"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 一覧はページごとに取得でき_次のページの有無が分かる() throws Exception {
        Post first = save(me, "1");
        Post second = save(me, "2");
        Post third = save(me, "3");

        getWithToken("/api/posts/me?size=2")
                .andExpect(jsonPath("$.posts[*].id").value(contains(id(third), id(second))))
                .andExpect(jsonPath("$.hasNext").value(true));
        getWithToken("/api/posts/me?size=2&page=1")
                .andExpect(jsonPath("$.posts[*].id").value(contains(id(first))))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 件数は1から50に丸められ_負のページは0として扱う() throws Exception {
        save(me, "1");

        getWithToken("/api/posts/me?size=1000").andExpect(jsonPath("$.size").value(50));
        getWithToken("/api/posts/me?size=0").andExpect(jsonPath("$.size").value(1));
        getWithToken("/api/posts/me?page=-1")
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.posts", hasSize(1)));
    }

    @Test
    void 投稿がなければ空の一覧() throws Exception {
        getWithToken("/api/posts/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ---- 認証 ----

    @Test
    void ログインしていなければ401() throws Exception {
        Post post = save(me, "投稿");

        mockMvc.perform(get("/api/posts/" + post.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts/me")).andExpect(status().isUnauthorized());
    }

    private Post save(User author, String title) {
        return postRepository.save(new Post(author, title, kireime, null, "説明", null,
                List.of("http://localhost/uploads/" + title + ".jpg"), List.of(furugi)));
    }

    private void setCreatedAt(Post post, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE posts SET created_at = ? WHERE id = ?", createdAt, post.getId());
    }

    /** JSON の数値（Integer）と比べるため int にする */
    private static int id(Post post) {
        return post.getId().intValue();
    }

    private ResultActions getWithToken(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }
}
