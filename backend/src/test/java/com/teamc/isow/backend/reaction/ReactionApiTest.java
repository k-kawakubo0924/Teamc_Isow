package com.teamc.isow.backend.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * いいね・お気に入り（POST / DELETE /api/posts/{id}/like・favorite）の確認。
 * API がトランザクションを自分で管理するため、テストはロールバックせず、終了時に作ったデータを消す。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReactionApiTest {

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

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private Post post;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(new User(ReactionTestSupport.EMAIL_A, "09000000010", "hash", "reaction_a"));
        userB = userRepository.save(new User(ReactionTestSupport.EMAIL_B, "09000000011", "hash", "reaction_b"));
        tokenA = ReactionTestSupport.token(jwtEncoder, userA.getId());
        tokenB = ReactionTestSupport.token(jwtEncoder, userB.getId());
        post = ReactionTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, userB);
    }

    @AfterEach
    void tearDown() {
        ReactionTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- 認証 ----

    @Test
    void ログインしていなければ401() throws Exception {
        String base = "/api/posts/" + post.getId();
        mockMvc.perform(post(base + "/like")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + "/like")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + "/favorite")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + "/favorite")).andExpect(status().isUnauthorized());
        assertThat(countLikes()).isZero();
        assertThat(countFavorites()).isZero();
    }

    // ---- いいね ----

    @Test
    void いいねすると件数が増え_同じユーザーが何回いいねしても1件のまま() throws Exception {
        for (int i = 0; i < 3; i++) {
            perform(post(likeUrl()), tokenA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true))
                    .andExpect(jsonPath("$.likeCount").value(1));
        }
        assertThat(countLikes()).isEqualTo(1);
    }

    @Test
    void 別のユーザーのいいねは件数に加わる_自分の投稿にもいいねできる() throws Exception {
        perform(post(likeUrl()), tokenA).andExpect(jsonPath("$.likeCount").value(1));
        // userB は投稿者本人
        perform(post(likeUrl()), tokenB).andExpect(jsonPath("$.likeCount").value(2));
    }

    @Test
    void いいねを取り消すと件数が減り_いいねしていなくても成功する() throws Exception {
        perform(post(likeUrl()), tokenA);
        perform(post(likeUrl()), tokenB);

        perform(delete(likeUrl()), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));
        // もう一度取り消しても（連打）エラーにならず、他の人のいいねは残る
        perform(delete(likeUrl()), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    // ---- お気に入り ----

    @Test
    void お気に入りは何回追加しても1件で_件数や他のユーザーの情報は返さない() throws Exception {
        for (int i = 0; i < 3; i++) {
            perform(post(favoriteUrl()), tokenA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.favorited").value(true))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(content().string(not(containsString("reaction_"))));
        }
        assertThat(countFavorites()).isEqualTo(1);
    }

    @Test
    void お気に入りから外せて_外していなくても成功する() throws Exception {
        perform(post(favoriteUrl()), tokenA);

        perform(delete(favoriteUrl()), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));
        perform(delete(favoriteUrl()), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));
        assertThat(countFavorites()).isZero();
    }

    @Test
    void いいねとお気に入りは別々に記録される() throws Exception {
        perform(post(likeUrl()), tokenA);
        perform(post(favoriteUrl()), tokenA);
        perform(delete(likeUrl()), tokenA);

        assertThat(countLikes()).isZero();
        assertThat(countFavorites()).isEqualTo(1);
    }

    // ---- 投稿 ----

    @Test
    void 存在しない投稿は404() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            post("/api/posts/999999/like"), delete("/api/posts/999999/like"),
            post("/api/posts/999999/favorite"), delete("/api/posts/999999/favorite")}) {
            perform(request, tokenA)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("投稿が見つかりません。"));
        }
    }

    @Test
    void 投稿を削除すると関連するいいねとお気に入りも消える() throws Exception {
        Post other = ReactionTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, userB);
        perform(post(likeUrl()), tokenA);
        perform(post(likeUrl()), tokenB);
        perform(post(favoriteUrl()), tokenA);
        perform(post("/api/posts/" + other.getId() + "/like"), tokenA);

        postRepository.deleteById(post.getId());

        // 削除した投稿の分だけ消え、他の投稿のいいねは残る
        assertThat(countLikes()).isEqualTo(1);
        assertThat(countFavorites()).isZero();
    }

    private String likeUrl() {
        return "/api/posts/" + post.getId() + "/like";
    }

    private String favoriteUrl() {
        return "/api/posts/" + post.getId() + "/favorite";
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private int countLikes() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM likes", Integer.class);
    }

    private int countFavorites() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM favorites", Integer.class);
    }
}
