package com.teamc.isow.backend.moderation;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 措置を受けた投稿・ユーザーの見え方（docs/admin.md「6. 措置」）。
 * 一覧からの漏れは VisibilityInventoryTest で確かめ、ここでは1件ずつの API・本人の見え方・ログイン・期限切れを確かめる
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModerationVisibilityTest {

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ModerationFixture f;

    @BeforeEach
    void setUp() {
        f = new ModerationFixture(mockMvc, jwtEncoder, userRepository, postRepository, fashionCategoryRepository,
                tagRepository, jdbcTemplate, passwordEncoder);
        f.createUsers();
    }

    @AfterEach
    void tearDown() {
        f.cleanUp();
    }

    // ---- 非公開にした投稿 ----

    @Test
    void 非公開の投稿は_投稿者本人は理由つきで見られ_ほかの人には存在しない投稿と同じ404() throws Exception {
        Post post = f.createPost(f.author, "非公開の投稿");
        f.hide(post);

        f.as(f.author, get("/api/posts/" + post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.hiddenReason").value("SPAM"));
        f.as(f.viewer, get("/api/posts/" + post.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("投稿が見つかりません。"));
        f.as(f.viewer, post("/api/posts/" + post.getId() + "/like")).andExpect(status().isNotFound());
        f.as(f.viewer, post("/api/posts/" + post.getId() + "/favorite")).andExpect(status().isNotFound());
        // 他人が編集・削除しようとしても、403 ではなく 404（非公開の投稿があることを伝えない）
        f.as(f.viewer, delete("/api/posts/" + post.getId())).andExpect(status().isNotFound());
    }

    @Test
    void 非公開になっていない投稿の応答は_hiddenがfalseで理由は空() throws Exception {
        Post post = f.createPost(f.author, "公開の投稿");

        f.as(f.viewer, get("/api/posts/" + post.getId()))
                .andExpect(jsonPath("$.hidden").value(false))
                .andExpect(jsonPath("$.hiddenReason").value(nullValue()));
    }

    @Test
    void 非公開の投稿は_投稿者の自分の一覧ではhiddenがtrueで出て_本人は削除できる() throws Exception {
        Post post = f.createPost(f.author, "自分で消す非公開の投稿");
        f.hide(post);

        f.as(f.author, get("/api/posts/me"))
                .andExpect(jsonPath("$.posts[0].id").value(post.getId()))
                .andExpect(jsonPath("$.posts[0].hidden").value(true));
        f.as(f.author, delete("/api/posts/" + post.getId())).andExpect(status().isNoContent());
    }

    @Test
    void 投稿者が非公開の投稿を編集しても_非公開のまま() throws Exception {
        Post post = f.createPost(f.author, "編集する非公開の投稿");
        f.hide(post);
        long categoryId = jdbcTemplate.queryForObject("SELECT fashion_category_id FROM posts WHERE id = ?", Long.class,
                post.getId());
        String tag = jdbcTemplate.queryForObject("SELECT t.name FROM tags t JOIN post_tags pt ON pt.tag_id = t.id "
                + "WHERE pt.post_id = ?", String.class, post.getId());

        f.as(f.author, put("/api/posts/" + post.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"直した\", \"fashionCategoryId\": " + categoryId + ", \"tags\": [\"" + tag
                                + "\"], \"description\": \"直した\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));
        f.as(f.viewer, get("/api/posts/" + post.getId())).andExpect(status().isNotFound());
    }

    @Test
    void 相手のプロフィールの投稿一覧と投稿数は_非公開の投稿を除き_本人のプロフィールでは数える() throws Exception {
        Post visible = f.createPost(f.author, "見える投稿");
        Post hidden = f.createPost(f.author, "見えない投稿");
        f.hide(hidden);

        f.as(f.viewer, get("/api/users/" + f.author.getId() + "/posts"))
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(visible.getId()));
        f.as(f.viewer, get("/api/users/" + f.author.getId())).andExpect(jsonPath("$.postCount").value(1));
        f.as(f.author, get("/api/users/me")).andExpect(jsonPath("$.postCount").value(2));
        f.as(f.author, get("/api/users/" + f.author.getId() + "/posts")).andExpect(jsonPath("$.posts.length()").value(2));
    }

    // ---- 利用停止 ----

    @Test
    void 利用停止中のユーザーは_ほかの人からはプロフィールもフォローも存在しないユーザーと同じ404() throws Exception {
        f.suspend(f.author, null);

        f.as(f.viewer, get("/api/users/" + f.author.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ユーザーが見つかりません。"));
        f.as(f.viewer, post("/api/users/" + f.author.getId() + "/follow")).andExpect(status().isNotFound());
        f.as(f.viewer, get("/api/users/" + f.author.getId() + "/posts")).andExpect(status().isNotFound());
    }

    @Test
    void 利用停止中は_持っているトークンで何をしても403で_理由と期限を返す() throws Exception {
        // 秒はあえて 00 にする。LocalDateTime.toString() は秒が 00 だと秒を省く（12:16）が、応答の JSON は省かない（12:16:00）。
        // 期待値を応答と同じ書き方（ISO_LOCAL_DATE_TIME）で作ることを、いちばん食い違いやすい時刻で毎回確かめる
        // （以前は toString() で作っていて、秒がちょうど 00 のときだけ落ちていた）
        LocalDateTime until = LocalDateTime.now().plusDays(7).withSecond(0).withNano(0);
        f.suspend(f.author, until);

        f.as(f.author, get("/api/users/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.suspendedUntil").value(until.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.suspensionReason").value("HARASSMENT"))
                .andExpect(jsonPath("$.suspensionReasonLabel").value("嫌がらせ・誹謗中傷"));
    }

    @Test
    void 利用停止中は_正しいパスワードでもログインできず_理由と期限を返す_無期限は期限が空() throws Exception {
        f.suspend(f.author, null);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"mod-author@example.com\", \"password\": \"" + ModerationFixture.PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.suspendedUntil").value(nullValue()))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void 利用停止中でも_パスワードが違えば今までどおり401で_利用停止中かどうかは分からない() throws Exception {
        f.suspend(f.author, null);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"mod-author@example.com\", \"password\": \"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void 期限を過ぎた利用停止は_その時点から本人は使え_ほかの人からも見える() throws Exception {
        f.suspend(f.author, LocalDateTime.now().minusMinutes(1));

        f.as(f.author, get("/api/users/me")).andExpect(status().isOk());
        f.as(f.viewer, get("/api/users/" + f.author.getId())).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"mod-author@example.com\", \"password\": \"" + ModerationFixture.PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }
}
