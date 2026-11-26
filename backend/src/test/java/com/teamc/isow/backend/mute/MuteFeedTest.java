package com.teamc.isow.backend.mute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.block.BlockFixture;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.nio.charset.StandardCharsets;
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

/**
 * ミュートで流れてくる一覧から除くこと（docs/profile.md「ミュート（投稿の非表示）」の「どこで隠れるか」）の、件数と細かい動きの確認。
 * どの API で隠すか・隠さないかの全体は VisibilityInventoryTest のミュートの欄で確かめる。
 *
 * <p>件数：隠す場所で件数を返しているのは投稿の検索（totalCount）だけ（ホームの3つのタブ・「フォロー中の投稿」は件数を返さない）。
 * 検索の件数は一覧と同じ条件で減らし、隠さない場所（プロフィールの投稿数・フォロー数・フォロワー数・ユーザー検索・お気に入り）の件数は変えない
 */
@SpringBootTest
@AutoConfigureMockMvc
class MuteFeedTest {

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

    private BlockFixture f;
    private User a;
    private User b;
    private User c;
    private Post postOfB;
    private Post postOfC;

    @BeforeEach
    void setUp() throws Exception {
        f = new BlockFixture(mockMvc, jwtEncoder, userRepository, postRepository, fashionCategoryRepository,
                tagRepository, jdbcTemplate);
        f.createUsers();
        a = f.a;
        b = f.b;
        c = f.c;
        f.createPost(a, "MUTEFEED_A");
        postOfB = f.createPost(b, "MUTEFEED_B");
        postOfC = f.createPost(c, "MUTEFEED_C");
        f.createPost(c, "MUTEFEED_C2");
    }

    @AfterEach
    void tearDown() {
        f.cleanUp();
    }

    private void mute(User from, User to) throws Exception {
        f.as(from, post("/api/users/" + to.getId() + "/mute")).andExpect(status().isNoContent());
    }

    private String body(User viewer, String path) throws Exception {
        return f.as(viewer, get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<Integer> postIds(String body) {
        return JsonPath.read(body, "$.posts[*].id");
    }

    private long number(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    // ---- 件数：隠す場所は一覧と同じ条件で減らす ----

    @Test
    void 投稿の検索の件数は_一覧と同じく_ミュートした人の投稿を数えない() throws Exception {
        String before = body(a, "/api/search/posts?q=MUTEFEED&size=50");
        assertThat(number(before, "$.totalCount")).as("前提：A・B・C2件の4件が出ている").isEqualTo(4);

        mute(a, c);

        String after = body(a, "/api/search/posts?q=MUTEFEED&size=50");
        assertThat(postIds(after)).doesNotContain(postOfC.getId().intValue());
        assertThat(number(after, "$.totalCount"))
                .as("一覧から C の2件を除いたのに、件数が減っていない（「〇件」と出ているのに並ぶ数が少なくなる）")
                .isEqualTo(2)
                .isEqualTo(postIds(after).size());
    }

    /**
     * 件数の SQL（countQuery）が実際に使われる場面で確かめる。1ページ目が埋まらないとき、Spring Data は件数の SQL を流さず、
     * 並んだ件数をそのまま件数にする（上のテストの size=50 では、件数の SQL に条件を付け忘れても気づけない。2026-11-26 に、
     * わざと付け忘れて確かめた）。そのため、1件ずつのページにして、件数の SQL を必ず流させる
     */
    @Test
    void 投稿の検索の件数は_ページをまたぐときも_ミュートした人の投稿を数えない() throws Exception {
        mute(a, c);

        String firstPage = body(a, "/api/search/posts?q=MUTEFEED&size=1&page=0");
        assertThat(postIds(firstPage)).hasSize(1);
        assertThat(number(firstPage, "$.totalCount"))
                .as("件数の SQL（countQuery）が、ミュートした C の投稿を数えている")
                .isEqualTo(2);
        String secondPage = body(a, "/api/search/posts?q=MUTEFEED&size=1&page=1");
        assertThat(postIds(secondPage)).hasSize(1);
        assertThat((Boolean) JsonPath.read(secondPage, "$.hasNext")).as("3ページ目はない（並ぶのは2件だけ）").isFalse();
    }

    // ---- 件数：隠さない場所は変えない ----

    @Test
    void ミュートしても_ミュートした人のプロフィールの投稿数_フォロー数_フォロワー数は変わらない() throws Exception {
        f.follow(c, b);
        f.follow(a, c);
        f.follow(b, c);
        String before = body(a, "/api/users/" + c.getId());

        mute(a, c);

        String after = body(a, "/api/users/" + c.getId());
        assertThat(number(after, "$.postCount")).as("プロフィールの投稿数").isEqualTo(number(before, "$.postCount")).isEqualTo(2);
        assertThat(number(after, "$.followingCount")).as("フォロー数").isEqualTo(number(before, "$.followingCount"));
        assertThat(number(after, "$.followerCount")).as("フォロワー数").isEqualTo(number(before, "$.followerCount"));
        assertThat(postIds(body(a, "/api/users/" + c.getId() + "/posts"))).as("プロフィールの投稿一覧").hasSize(2);
    }

    @Test
    void ミュートしても_自分のフォロー数と_ほかの人のフォロワー数は変わらない() throws Exception {
        f.follow(a, c);
        f.follow(c, a);
        f.follow(c, b);
        String mineBefore = body(a, "/api/users/" + a.getId());
        String otherBefore = body(a, "/api/users/" + b.getId());

        mute(a, c);

        String mineAfter = body(a, "/api/users/" + a.getId());
        String otherAfter = body(a, "/api/users/" + b.getId());
        assertThat(number(mineAfter, "$.followingCount")).as("自分のフォロー数").isEqualTo(number(mineBefore, "$.followingCount"));
        assertThat(number(mineAfter, "$.followerCount")).as("自分のフォロワー数").isEqualTo(number(mineBefore, "$.followerCount"));
        assertThat(number(otherAfter, "$.followerCount")).as("B のフォロワー数（C を含む）")
                .isEqualTo(number(otherBefore, "$.followerCount"));
    }

    @Test
    void ミュートしても_ユーザー検索の件数とお気に入りの件数は変わらない() throws Exception {
        f.favorite(a, postOfC);
        f.favorite(a, postOfB);
        String usersBefore = body(a, "/api/search/users?q=blkuser");

        mute(a, c);

        String usersAfter = body(a, "/api/search/users?q=blkuser");
        assertThat(number(usersAfter, "$.totalCount")).as("ユーザー検索の件数").isEqualTo(number(usersBefore, "$.totalCount"));
        assertThat(postIds(body(a, "/api/users/me/favorites")))
                .as("お気に入りは自分で選んだもので、隠さない")
                .containsExactlyInAnyOrder(postOfC.getId().intValue(), postOfB.getId().intValue());
    }

    // ---- 細かい動き ----

    @Test
    void ほかの人がしたミュートは_自分の一覧に影響しない() throws Exception {
        mute(b, c);

        assertThat(postIds(body(a, "/api/posts?tab=latest"))).as("B のミュートで A の一覧から C が消えた")
                .contains(postOfC.getId().intValue());
        assertThat(number(body(a, "/api/search/posts?q=MUTEFEED&size=50"), "$.totalCount")).isEqualTo(4);
    }

    @Test
    void フォローしていても_フォロー中のタブから隠れ_フォローは外れない() throws Exception {
        f.follow(a, c);
        assertThat(postIds(body(a, "/api/posts?tab=following"))).as("前提").contains(postOfC.getId().intValue());

        mute(a, c);

        assertThat(postIds(body(a, "/api/posts?tab=following"))).doesNotContain(postOfC.getId().intValue());
        assertThat((Boolean) JsonPath.read(body(a, "/api/users/" + c.getId()), "$.followingByMe")).isTrue();
    }

    @Test
    void 解除すると_流れてくる一覧に戻る() throws Exception {
        mute(a, c);
        assertThat(postIds(body(a, "/api/posts?tab=latest"))).doesNotContain(postOfC.getId().intValue());

        f.as(a, delete("/api/users/" + c.getId() + "/mute")).andExpect(status().isNoContent());

        assertThat(postIds(body(a, "/api/posts?tab=latest"))).contains(postOfC.getId().intValue());
        assertThat(postIds(body(a, "/api/posts?tab=recommended"))).contains(postOfC.getId().intValue());
    }
}
