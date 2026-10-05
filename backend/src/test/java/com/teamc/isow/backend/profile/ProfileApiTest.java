package com.teamc.isow.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.follow.Follow;
import com.teamc.isow.backend.follow.FollowRepository;
import com.teamc.isow.backend.master.BodyType;
import com.teamc.isow.backend.master.BodyTypeRepository;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.master.PersonalColor;
import com.teamc.isow.backend.master.PersonalColorRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.reaction.PostFavorite;
import com.teamc.isow.backend.reaction.PostFavoriteRepository;
import com.teamc.isow.backend.reaction.PostLike;
import com.teamc.isow.backend.reaction.PostLikeRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

/** プロフィール（GET /api/users/me・/{id}・/{id}/posts・/me/favorites）の確認 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

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
    private BodyTypeRepository bodyTypeRepository;

    @Autowired
    private PersonalColorRepository personalColorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User other;
    private User third;
    private String token;
    private BodyType bodyType;
    private PersonalColor personalColor;

    @BeforeEach
    void setUp() {
        bodyType = bodyTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
        personalColor = personalColorRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
        me = userRepository.save(new User(ProfileTestSupport.EMAIL_ME, "09000000030", "hash", "profile_me"));
        User o = new User(ProfileTestSupport.EMAIL_OTHER, "09000000031", "hash", "profile_other");
        o.updateProfile("ほか", Gender.FEMALE, 158, AgeGroup.EARLY_20S, bodyType, personalColor);
        o.changeProfileImageUrl("http://localhost/uploads/icon.jpg");
        other = userRepository.save(o);
        third = userRepository.save(new User(ProfileTestSupport.EMAIL_THIRD, "09000000032", "hash", "profile_third"));
        token = ProfileTestSupport.token(jwtEncoder, me.getId());
    }

    @AfterEach
    void tearDown() {
        ProfileTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- 認証・存在しないユーザー ----

    @Test
    void トークンがない場合は401() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/" + other.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/" + other.getId() + "/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/favorites")).andExpect(status().isUnauthorized());
    }

    @Test
    void 存在しないユーザーは404() throws Exception {
        long missing = third.getId() + 1000;
        perform("/api/users/" + missing).andExpect(status().isNotFound());
        perform("/api/users/" + missing + "/posts").andExpect(status().isNotFound());
    }

    // ---- プロフィール ----

    @Test
    void 相手のプロフィールは登録した項目とフォロー数と自分がフォロー済みかを返す() throws Exception {
        followRepository.save(new Follow(me, other));
        followRepository.save(new Follow(third, other));
        followRepository.save(new Follow(other, third));
        // 解除済みのフォローは数えない
        Follow unfollowed = followRepository.save(new Follow(other, me));
        jdbcTemplate.update("UPDATE follows SET active = NULL, unfollowed_at = CURRENT_TIMESTAMP WHERE id = ?",
                unfollowed.getId());

        perform("/api/users/" + other.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other.getId()))
                .andExpect(jsonPath("$.username").value("profile_other"))
                .andExpect(jsonPath("$.displayName").value("ほか"))
                .andExpect(jsonPath("$.profileImageUrl").value("http://localhost/uploads/icon.jpg"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.heightCm").value(158))
                .andExpect(jsonPath("$.ageGroup").value("EARLY_20S"))
                .andExpect(jsonPath("$.bodyType.id").value(bodyType.getId()))
                .andExpect(jsonPath("$.bodyType.name").value(bodyType.getName()))
                .andExpect(jsonPath("$.personalColor.id").value(personalColor.getId()))
                .andExpect(jsonPath("$.personalColor.name").value(personalColor.getName()))
                .andExpect(jsonPath("$.followingCount").value(1))
                .andExpect(jsonPath("$.followerCount").value(2))
                .andExpect(jsonPath("$.me").value(false))
                .andExpect(jsonPath("$.followingByMe").value(true))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());

        perform("/api/users/" + third.getId())
                .andExpect(jsonPath("$.followingByMe").value(false));
    }

    @Test
    void 自分のプロフィールは未設定の項目がnullで_フォロー済みかはnull() throws Exception {
        followRepository.save(new Follow(me, other));

        for (String url : new String[] {"/api/users/me", "/api/users/" + me.getId()}) {
            perform(url)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(me.getId()))
                    .andExpect(jsonPath("$.username").value("profile_me"))
                    // 新規登録時はユーザ名が表示名になる
                    .andExpect(jsonPath("$.displayName").value("profile_me"))
                    .andExpect(jsonPath("$.gender").isEmpty())
                    .andExpect(jsonPath("$.heightCm").isEmpty())
                    .andExpect(jsonPath("$.ageGroup").isEmpty())
                    .andExpect(jsonPath("$.bodyType").isEmpty())
                    .andExpect(jsonPath("$.personalColor").isEmpty())
                    .andExpect(jsonPath("$.profileImageUrl").isEmpty())
                    .andExpect(jsonPath("$.followingCount").value(1))
                    .andExpect(jsonPath("$.followerCount").value(0))
                    .andExpect(jsonPath("$.me").value(true))
                    .andExpect(jsonPath("$.followingByMe").isEmpty());
        }
    }

    // ---- 投稿一覧・お気に入り一覧 ----

    @Test
    void ユーザーの投稿一覧はホームと同じ形で新しい順に返す() throws Exception {
        Post older = ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, other, "old");
        Post newer = ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, other, "new");
        ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, third, "third");
        setCreatedAt("posts", older.getId(), LocalDateTime.of(2026, 9, 1, 12, 0));
        setCreatedAt("posts", newer.getId(), LocalDateTime.of(2026, 10, 1, 12, 0));
        likeRepository.save(new PostLike(me, newer));
        likeRepository.save(new PostLike(third, newer));
        favoriteRepository.save(new PostFavorite(me, older));

        perform("/api/users/" + other.getId() + "/posts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.posts[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.posts[0].thumbnailUrl").value("http://localhost/uploads/new-1.jpg"))
                .andExpect(jsonPath("$.posts[0].fashionCategory.id").exists())
                .andExpect(jsonPath("$.posts[0].author.id").value(other.getId()))
                .andExpect(jsonPath("$.posts[0].author.username").value("profile_other"))
                .andExpect(jsonPath("$.posts[0].author.heightCm").value(158))
                .andExpect(jsonPath("$.posts[0].likeCount").value(2))
                .andExpect(jsonPath("$.posts[0].likedByMe").value(true))
                .andExpect(jsonPath("$.posts[0].favoritedByMe").value(false))
                .andExpect(jsonPath("$.posts[0].createdAt").value("2026-10-01T12:00:00"))
                .andExpect(jsonPath("$.posts[1].id").value(older.getId()))
                .andExpect(jsonPath("$.posts[1].likedByMe").value(false))
                .andExpect(jsonPath("$.posts[1].favoritedByMe").value(true));
    }

    @Test
    void プロフィールは投稿の件数を返す() throws Exception {
        ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, other, "a");
        ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, other, "b");
        ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, third, "c");

        perform("/api/users/" + other.getId()).andExpect(jsonPath("$.postCount").value(2));
        perform("/api/users/me").andExpect(jsonPath("$.postCount").value(0));
    }

    @Test
    void フォロー中の投稿は有効にフォローしている人の投稿だけを返し_同じseedならページをまたいでも重複しない() throws Exception {
        // other は me と third をフォローしている。me へのフォローは解除済み
        followRepository.save(new Follow(other, third));
        Follow unfollowed = followRepository.save(new Follow(other, me));
        jdbcTemplate.update("UPDATE follows SET active = NULL, unfollowed_at = CURRENT_TIMESTAMP WHERE id = ?",
                unfollowed.getId());
        Set<Long> expected = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            expected.add(ProfileTestSupport.savePost(
                    postRepository, fashionCategoryRepository, tagRepository, third, "t" + i).getId());
        }
        ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, me, "unfollowed");
        ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, other, "own");

        List<Long> seed1 = followingPostIds(other, 12345, 3);
        assertThat(seed1).hasSize(7).doesNotHaveDuplicates();
        assertThat(new HashSet<>(seed1)).isEqualTo(expected);
        // 同じ seed なら同じ並び順
        assertThat(followingPostIds(other, 12345, 3)).isEqualTo(seed1);
        // 別の seed では並び順が変わる（7件なので、偶然同じ順になることはまずない）
        assertThat(followingPostIds(other, 987_654_321, 3)).isNotEqualTo(seed1);
    }

    @Test
    void フォロー中の投稿のseedが範囲外なら400_存在しないユーザーは404() throws Exception {
        for (String seed : new String[] {"0", "-1", "2147483647"}) {
            perform("/api/users/" + other.getId() + "/following-posts?seed=" + seed)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.seed").exists());
        }
        perform("/api/users/" + (third.getId() + 1000) + "/following-posts?seed=1").andExpect(status().isNotFound());
    }

    /** フォロー中の投稿を、size 件ずつ最後のページまで読み、ID を順に並べる */
    private List<Long> followingPostIds(User user, long seed, int size) throws Exception {
        List<Long> ids = new ArrayList<>();
        boolean hasNext = true;
        for (int page = 0; hasNext; page++) {
            String body = perform("/api/users/" + user.getId() + "/following-posts?seed=" + seed
                    + "&size=" + size + "&page=" + page)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Number> pageIds = JsonPath.read(body, "$.posts[*].id");
            pageIds.forEach(id -> ids.add(id.longValue()));
            hasNext = JsonPath.read(body, "$.hasNext");
        }
        return ids;
    }

    @Test
    void 投稿がないユーザーの一覧は空() throws Exception {
        perform("/api/users/" + third.getId() + "/posts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 投稿一覧はページに分けて返す() throws Exception {
        for (int i = 0; i < 3; i++) {
            ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, other, "p" + i);
        }
        perform("/api/users/" + other.getId() + "/posts?size=2")
                .andExpect(jsonPath("$.posts", hasSize(2)))
                .andExpect(jsonPath("$.hasNext").value(true));
        perform("/api/users/" + other.getId() + "/posts?size=2&page=1")
                .andExpect(jsonPath("$.posts", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void お気に入り一覧は自分がお気に入りにした投稿をお気に入りにした新しい順に返す() throws Exception {
        Post a = ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, other, "a");
        Post b = ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, third, "b");
        Post notMine = ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, other, "c");
        // 投稿日時とは逆の順でお気に入りにする（並び順がお気に入りにした日時であることを確かめる）
        PostFavorite favB = favoriteRepository.save(new PostFavorite(me, b));
        PostFavorite favA = favoriteRepository.save(new PostFavorite(me, a));
        setCreatedAt("favorites", favB.getId(), LocalDateTime.of(2026, 9, 1, 12, 0));
        setCreatedAt("favorites", favA.getId(), LocalDateTime.of(2026, 10, 1, 12, 0));
        // 他の人のお気に入りは含めない
        favoriteRepository.save(new PostFavorite(third, notMine));

        perform("/api/users/me/favorites")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", hasSize(2)))
                .andExpect(jsonPath("$.posts[0].id").value(a.getId()))
                .andExpect(jsonPath("$.posts[0].author.username").value("profile_other"))
                .andExpect(jsonPath("$.posts[0].favoritedByMe").value(true))
                .andExpect(jsonPath("$.posts[1].id").value(b.getId()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 他の人のお気に入り一覧は取得できない() throws Exception {
        Post post = ProfileTestSupport.savePost(postRepository, fashionCategoryRepository, tagRepository, third, "fav");
        favoriteRepository.save(new PostFavorite(other, post));

        // お気に入りは本人にだけ見せるため、他の人のお気に入り一覧の API は無い
        perform("/api/users/" + other.getId() + "/favorites").andExpect(status().isNotFound());
        // 自分のお気に入り一覧には、他の人のお気に入りは含まれない
        perform("/api/users/me/favorites").andExpect(jsonPath("$.posts", hasSize(0)));
        // 他の人の投稿一覧・プロフィールにも、その人のお気に入りの状態は出ない（favoritedByMe は見ている自分の状態）
        perform("/api/users/" + third.getId() + "/posts")
                .andExpect(jsonPath("$.posts[0].id").value(post.getId()))
                .andExpect(jsonPath("$.posts[0].favoritedByMe").value(false));
        perform("/api/users/" + other.getId()).andExpect(jsonPath("$.favorites").doesNotExist());
    }

    private ResultActions perform(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private void setCreatedAt(String table, Long id, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE " + table + " SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), id);
    }
}
