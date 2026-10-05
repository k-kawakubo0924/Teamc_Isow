package com.teamc.isow.backend.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.follow.Follow;
import com.teamc.isow.backend.follow.FollowRepository;
import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.reaction.PostFavorite;
import com.teamc.isow.backend.reaction.PostFavoriteRepository;
import com.teamc.isow.backend.reaction.PostLike;
import com.teamc.isow.backend.reaction.PostLikeRepository;
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
 * ホームの投稿一覧（GET /api/posts）と、詳細・自分の投稿一覧に追加したいいね・お気に入りの項目の確認。
 * 投稿・いいね・お気に入りは Repository で直接作る（画像ファイルは使わないため、URL だけを持たせる）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimelineApiTest {

    static final String OTHER_EMAIL_1 = "timeline-other1@example.com";
    static final String OTHER_EMAIL_2 = "timeline-other2@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository likeRepository;

    @Autowired
    private PostFavoriteRepository favoriteRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User other1;
    private User other2;
    private String token;
    private FashionCategory fashion;
    private Tag tag;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(PostApiTestSupport.EMAIL, "09000000000", "hash", "timeline_me"));
        other1 = userRepository.save(new User(OTHER_EMAIL_1, "09000000001", "hash", "timeline_other1"));
        other2 = userRepository.save(new User(OTHER_EMAIL_2, "09000000002", "hash", "timeline_other2"));
        token = PostApiTestSupport.token(jwtEncoder, me.getId());
        fashion = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
        tag = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
    }

    @AfterEach
    void tearDown() {
        // follows はユーザーを参照しているため先に消す。投稿を消すと likes・favorites は DB の連鎖削除で消える
        jdbcTemplate.update("DELETE FROM follows");
        PostApiTestSupport.cleanUp(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", OTHER_EMAIL_1, OTHER_EMAIL_2);
    }

    // ---- 一覧の内容 ----

    @Test
    void 一覧の1件には1枚目の写真_投稿者_身長_いいね数_自分のいいねとお気に入りの状態が含まれる() throws Exception {
        jdbcTemplate.update("UPDATE users SET height_cm = 172 WHERE id = ?", other1.getId());
        Post post = save(other1, "http://localhost/uploads/first.jpg", "http://localhost/uploads/second.jpg");
        likeRepository.save(new PostLike(me, post));
        likeRepository.save(new PostLike(other2, post));
        favoriteRepository.save(new PostFavorite(me, post));

        getWithToken("/api/posts?tab=latest")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", hasSize(1)))
                .andExpect(jsonPath("$.posts[0].id").value(post.getId()))
                .andExpect(jsonPath("$.posts[0].thumbnailUrl").value("http://localhost/uploads/first.jpg"))
                .andExpect(jsonPath("$.posts[0].fashionCategory.name").value(fashion.getName()))
                .andExpect(jsonPath("$.posts[0].author.id").value(other1.getId()))
                .andExpect(jsonPath("$.posts[0].author.username").value("timeline_other1"))
                .andExpect(jsonPath("$.posts[0].author.heightCm").value(172))
                .andExpect(jsonPath("$.posts[0].likeCount").value(2))
                .andExpect(jsonPath("$.posts[0].likedByMe").value(true))
                .andExpect(jsonPath("$.posts[0].favoritedByMe").value(true))
                .andExpect(jsonPath("$.posts[0].createdAt").isNotEmpty())
                // 一覧では2枚目以降の写真・題名・説明は返さない
                .andExpect(jsonPath("$.posts[0].imageUrls").doesNotExist())
                .andExpect(jsonPath("$.posts[0].description").doesNotExist());
    }

    @Test
    void 身長が未設定ならnull_他人のいいねやお気に入りは自分の状態にならない() throws Exception {
        Post post = save(other1, "http://localhost/uploads/a.jpg");
        likeRepository.save(new PostLike(other2, post));
        favoriteRepository.save(new PostFavorite(other2, post));

        getWithToken("/api/posts?tab=latest")
                .andExpect(jsonPath("$.posts[0].author.heightCm").value(nullValue()))
                .andExpect(jsonPath("$.posts[0].likeCount").value(1))
                .andExpect(jsonPath("$.posts[0].likedByMe").value(false))
                .andExpect(jsonPath("$.posts[0].favoritedByMe").value(false));
    }

    // ---- 並び順 ----

    @Test
    void 新着は投稿日時の新しい順() throws Exception {
        Post oldest = save(other1, "http://localhost/uploads/1.jpg");
        Post newest = save(other1, "http://localhost/uploads/2.jpg");
        Post middle = save(other2, "http://localhost/uploads/3.jpg");
        // 作成した順（ID 順）とは違う投稿日時にして、投稿日時で並ぶことを確かめる
        setCreatedAt(oldest, LocalDateTime.of(2026, 1, 1, 0, 0));
        setCreatedAt(newest, LocalDateTime.of(2026, 3, 1, 0, 0));
        setCreatedAt(middle, LocalDateTime.of(2026, 2, 1, 0, 0));

        getWithToken("/api/posts?tab=latest")
                .andExpect(jsonPath("$.tab").value("latest"))
                .andExpect(jsonPath("$.posts[*].id").value(contains(id(newest), id(middle), id(oldest))));
    }

    @Test
    void おすすめはいいね数の多い順で_同じなら新しい順() throws Exception {
        Post noLikes = save(other1, "http://localhost/uploads/1.jpg");
        Post twoLikes = save(other1, "http://localhost/uploads/2.jpg");
        Post oneLikeOld = save(other1, "http://localhost/uploads/3.jpg");
        Post oneLikeNew = save(other1, "http://localhost/uploads/4.jpg");
        likeRepository.save(new PostLike(me, twoLikes));
        likeRepository.save(new PostLike(other2, twoLikes));
        likeRepository.save(new PostLike(me, oneLikeOld));
        likeRepository.save(new PostLike(me, oneLikeNew));
        setCreatedAt(oneLikeOld, LocalDateTime.of(2026, 1, 1, 0, 0));
        setCreatedAt(oneLikeNew, LocalDateTime.of(2026, 2, 1, 0, 0));

        getWithToken("/api/posts?tab=recommended")
                .andExpect(jsonPath("$.tab").value("recommended"))
                .andExpect(jsonPath("$.posts[*].id").value(contains(id(twoLikes), id(oneLikeNew), id(oneLikeOld), id(noLikes))))
                .andExpect(jsonPath("$.posts[*].likeCount").value(contains(2, 1, 1, 0)));
    }

    @Test
    void タブを省略するとおすすめ_正しくない値は400() throws Exception {
        getWithToken("/api/posts").andExpect(status().isOk()).andExpect(jsonPath("$.tab").value("recommended"));
        getWithToken("/api/posts?tab=popular")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tab").value("表示するタブの指定が正しくありません"));
    }

    @Test
    void フォロー中は有効にフォローしている人の投稿だけを新しい順に返す() throws Exception {
        followRepository.save(new Follow(me, other1));
        Follow unfollowed = followRepository.save(new Follow(me, other2));
        jdbcTemplate.update("UPDATE follows SET active = NULL, unfollowed_at = CURRENT_TIMESTAMP WHERE id = ?",
                unfollowed.getId());
        Post older = save(other1, "http://localhost/uploads/older.jpg");
        Post newer = save(other1, "http://localhost/uploads/newer.jpg");
        setCreatedAt(older, LocalDateTime.of(2026, 1, 1, 0, 0));
        setCreatedAt(newer, LocalDateTime.of(2026, 2, 1, 0, 0));
        save(other2, "http://localhost/uploads/unfollowed.jpg");
        save(me, "http://localhost/uploads/mine.jpg");

        getWithToken("/api/posts?tab=following")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tab").value("following"))
                .andExpect(jsonPath("$.posts[*].id").value(contains(id(newer), id(older))))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void フォロー中のユーザーがいなければフォロー中は空() throws Exception {
        save(other1, "http://localhost/uploads/1.jpg");
        getWithToken("/api/posts?tab=following")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", hasSize(0)));
    }

    // ---- ページ送り ----

    @Test
    void ページごとに取得でき_次のページの有無が分かる() throws Exception {
        Post first = save(other1, "http://localhost/uploads/1.jpg");
        Post second = save(other1, "http://localhost/uploads/2.jpg");
        Post third = save(other1, "http://localhost/uploads/3.jpg");

        getWithToken("/api/posts?tab=latest&size=2")
                .andExpect(jsonPath("$.posts[*].id").value(contains(id(third), id(second))))
                .andExpect(jsonPath("$.hasNext").value(true));
        getWithToken("/api/posts?tab=latest&size=2&page=1")
                .andExpect(jsonPath("$.posts[*].id").value(contains(id(first))))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 件数は1から50に丸められ_投稿がなければ空の一覧() throws Exception {
        getWithToken("/api/posts?size=1000").andExpect(jsonPath("$.size").value(50));
        getWithToken("/api/posts?size=0&page=-1")
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.posts", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void ログインしていなければ401() throws Exception {
        mockMvc.perform(get("/api/posts")).andExpect(status().isUnauthorized());
    }

    // ---- 詳細・自分の投稿一覧 ----

    @Test
    void 詳細と自分の投稿一覧にもいいね数と自分のいいね_お気に入りの状態が含まれる() throws Exception {
        Post mine = save(me, "http://localhost/uploads/mine.jpg");
        likeRepository.save(new PostLike(other1, mine));
        likeRepository.save(new PostLike(other2, mine));
        favoriteRepository.save(new PostFavorite(me, mine));

        getWithToken("/api/posts/" + mine.getId())
                .andExpect(jsonPath("$.likeCount").value(2))
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andExpect(jsonPath("$.favoritedByMe").value(true));
        getWithToken("/api/posts/me")
                .andExpect(jsonPath("$.posts[0].likeCount").value(2))
                .andExpect(jsonPath("$.posts[0].likedByMe").value(false))
                .andExpect(jsonPath("$.posts[0].favoritedByMe").value(true));
    }

    @Test
    void 他人のお気に入りは詳細と自分の投稿一覧でも漏れない() throws Exception {
        // 自分の投稿を他人がお気に入りにしている（投稿者本人にも見せない）
        Post mine = save(me, "http://localhost/uploads/mine.jpg");
        favoriteRepository.save(new PostFavorite(other1, mine));
        favoriteRepository.save(new PostFavorite(other2, mine));

        for (String url : new String[] {"/api/posts/" + mine.getId(), "/api/posts/me", "/api/posts?tab=latest"}) {
            String body = getWithToken(url).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            // 自分はお気に入りにしていないので false。誰がお気に入りにしたか・何件かも返さない
            assertThat(body).contains("\"favoritedByMe\":false")
                    .doesNotContain("\"favoritedByMe\":true")
                    .doesNotContain("favoriteCount")
                    .doesNotContain("timeline_other");
        }
    }

    private Post save(User author, String... imageUrls) {
        return postRepository.save(new Post(author, "題名", fashion, null, "説明", null, List.of(imageUrls), List.of(tag)));
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
