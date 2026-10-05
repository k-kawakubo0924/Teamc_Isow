package com.teamc.isow.backend.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * フォロー（POST / DELETE /api/users/{id}/follow）と、フォロー中一覧・フォロワー一覧の確認。
 * API がトランザクションを自分で管理するため、テストはロールバックせず、終了時に作ったデータを消す。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FollowApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User userA;
    private User userB;
    private User userC;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(new User(FollowTestSupport.EMAIL_A, "09000000020", "hash", "follow_a"));
        User b = new User(FollowTestSupport.EMAIL_B, "09000000021", "hash", "follow_b");
        b.updateProfile("びー", Gender.FEMALE, 158, null, null, null);
        userB = userRepository.save(b);
        userC = userRepository.save(new User(FollowTestSupport.EMAIL_C, "09000000022", "hash", "follow_c"));
        tokenA = FollowTestSupport.token(jwtEncoder, userA.getId());
        tokenB = FollowTestSupport.token(jwtEncoder, userB.getId());
    }

    @AfterEach
    void tearDown() {
        FollowTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- 認証・入力 ----

    @Test
    void トークンがない場合は401() throws Exception {
        mockMvc.perform(post(followUrl(userB))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/" + userB.getId() + "/followings")).andExpect(status().isUnauthorized());
    }

    @Test
    void 自分自身はフォローできず400() throws Exception {
        perform(post(followUrl(userA)), tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("自分自身はフォローできません。"));
        assertThat(rowCount()).isZero();
    }

    @Test
    void 存在しないユーザーは404() throws Exception {
        long missing = userC.getId() + 1000;
        perform(post("/api/users/" + missing + "/follow"), tokenA).andExpect(status().isNotFound());
        perform(delete("/api/users/" + missing + "/follow"), tokenA).andExpect(status().isNotFound());
        perform(get("/api/users/" + missing + "/followings"), tokenA).andExpect(status().isNotFound());
        perform(get("/api/users/" + missing + "/followers"), tokenA).andExpect(status().isNotFound());
    }

    @Test
    void 並び順の指定が正しくない場合は400() throws Exception {
        perform(get("/api/users/" + userA.getId() + "/followings").param("sort", "popular"), tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sort").exists());
    }

    // ---- フォロー・解除 ----

    @Test
    void フォローすると有効な行ができ_連打しても1行のまま() throws Exception {
        for (int i = 0; i < 3; i++) {
            perform(post(followUrl(userB)), tokenA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.following").value(true))
                    .andExpect(jsonPath("$.followerCount").value(1));
        }
        assertThat(rowCount()).isEqualTo(1);
        Follow follow = followRepository.findAll().get(0);
        assertThat(follow.isActive()).isTrue();
        assertThat(follow.getUnfollowedAt()).isNull();
        assertThat(follow.getRestoredAt()).isNull();
    }

    @Test
    void 解除しても行は消えず解除日時が入り_連打してもエラーにならない() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) {
            perform(delete(followUrl(userB)), tokenA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.following").value(false))
                    .andExpect(jsonPath("$.followerCount").value(0));
        }
        assertThat(rowCount()).isEqualTo(1);
        Follow follow = followRepository.findAll().get(0);
        assertThat(follow.isActive()).isFalse();
        assertThat(follow.getUnfollowedAt()).isNotNull();
    }

    @Test
    void フォローしていない相手の解除も成功する() throws Exception {
        perform(delete(followUrl(userB)), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false));
        assertThat(rowCount()).isZero();
    }

    @Test
    void 解除から5分以内の再フォローは元の行を戻し_フォロー日時は変えない() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        LocalDateTime followedAt = LocalDateTime.of(2026, 10, 1, 12, 0);
        jdbcTemplate.update("UPDATE follows SET followed_at = ?", Timestamp.valueOf(followedAt));
        perform(delete(followUrl(userB)), tokenA).andExpect(status().isOk());
        setUnfollowedMinutesAgo(4);

        perform(post(followUrl(userB)), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        assertThat(rowCount()).isEqualTo(1);
        Follow follow = followRepository.findAll().get(0);
        assertThat(follow.isActive()).isTrue();
        assertThat(follow.getUnfollowedAt()).isNull();
        assertThat(follow.getFollowedAt()).isEqualTo(followedAt);
        // 通知を送らない再フォローと判定できる
        assertThat(follow.getRestoredAt()).isNotNull();
    }

    @Test
    void 解除から5分を過ぎた再フォローは新しい行を作る() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        perform(delete(followUrl(userB)), tokenA).andExpect(status().isOk());
        setUnfollowedMinutesAgo(6);

        perform(post(followUrl(userB)), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1));

        assertThat(rowCount()).isEqualTo(2);
        Follow active = followRepository.findAll().stream().filter(Follow::isActive).findFirst().orElseThrow();
        assertThat(active.getRestoredAt()).isNull();
    }

    @Test
    void DBの制約で同じ組み合わせの有効なフォローは1つまで_解除済みはいくつでもよい() {
        followRepository.saveAndFlush(new Follow(userA, userB));
        jdbcTemplate.update("UPDATE follows SET active = NULL, unfollowed_at = CURRENT_TIMESTAMP");
        followRepository.saveAndFlush(new Follow(userA, userB));
        jdbcTemplate.update("UPDATE follows SET active = NULL, unfollowed_at = CURRENT_TIMESTAMP");
        followRepository.saveAndFlush(new Follow(userA, userB));

        assertThatThrownBy(() -> followRepository.saveAndFlush(new Follow(userA, userB)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> followRepository.saveAndFlush(new Follow(userA, userA)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rowCount()).isEqualTo(3);
    }

    // ---- 一覧 ----

    @Test
    void フォロー中一覧は相手の情報とフォロー日時を返し_新しい順と古い順で並べられる() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        perform(post(followUrl(userC)), tokenA).andExpect(status().isOk());
        setFollowedAt(userB, LocalDateTime.of(2026, 9, 1, 12, 0));
        setFollowedAt(userC, LocalDateTime.of(2026, 10, 1, 12, 0));

        perform(get("/api/users/" + userA.getId() + "/followings"), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.sort").value("newest"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.users", hasSize(2)))
                .andExpect(jsonPath("$.users[0].id").value(userC.getId()))
                .andExpect(jsonPath("$.users[1].id").value(userB.getId()))
                .andExpect(jsonPath("$.users[1].username").value("follow_b"))
                .andExpect(jsonPath("$.users[1].displayName").value("びー"))
                .andExpect(jsonPath("$.users[1].heightCm").value(158))
                .andExpect(jsonPath("$.users[1].gender").value("FEMALE"))
                .andExpect(jsonPath("$.users[1].followedAt").value("2026-09-01T12:00:00"))
                .andExpect(jsonPath("$.users[1].followingByMe").value(true))
                .andExpect(jsonPath("$.users[1].email").doesNotExist());

        perform(get("/api/users/" + userA.getId() + "/followings").param("sort", "oldest"), tokenA)
                .andExpect(jsonPath("$.sort").value("oldest"))
                .andExpect(jsonPath("$.users[0].id").value(userB.getId()))
                .andExpect(jsonPath("$.users[1].id").value(userC.getId()));
    }

    @Test
    void 自分のフォロー中一覧には解除から5分以内のものも含め_フォロー数には数えない() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        perform(post(followUrl(userC)), tokenA).andExpect(status().isOk());
        perform(delete(followUrl(userB)), tokenA).andExpect(status().isOk());

        perform(get("/api/users/" + userA.getId() + "/followings"), tokenA)
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.users", hasSize(2)))
                .andExpect(jsonPath("$.users[?(@.id == " + userB.getId() + ")].followingByMe").value(false))
                .andExpect(jsonPath("$.users[?(@.id == " + userC.getId() + ")].followingByMe").value(true));

        // 5分を過ぎると出なくなる
        setUnfollowedMinutesAgo(6);
        perform(get("/api/users/" + userA.getId() + "/followings"), tokenA)
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].id").value(userC.getId()));
    }

    @Test
    void 他の人のフォロー中一覧には解除から5分以内のものを含めない() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        perform(post(followUrl(userC)), tokenA).andExpect(status().isOk());
        perform(delete(followUrl(userC)), tokenA).andExpect(status().isOk());

        perform(get("/api/users/" + userA.getId() + "/followings"), tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].id").value(userB.getId()))
                // 見ている B は自分自身をフォローしていない
                .andExpect(jsonPath("$.users[0].followingByMe").value(false));
    }

    @Test
    void フォロワー一覧は有効なフォローだけを返し_見ている人がフォローしているかを返す() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        perform(post("/api/users/" + userA.getId() + "/follow"), tokenB).andExpect(status().isOk());
        String tokenC = FollowTestSupport.token(jwtEncoder, userC.getId());
        perform(post(followUrl(userB)), tokenC).andExpect(status().isOk());
        perform(delete(followUrl(userB)), tokenC).andExpect(status().isOk());

        perform(get("/api/users/" + userB.getId() + "/followers"), tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].id").value(userA.getId()))
                .andExpect(jsonPath("$.users[0].followingByMe").value(true));
    }

    @Test
    void 検索欄の文字でユーザー名と表示名を絞り込み_件数は絞り込む前のまま() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        perform(post(followUrl(userC)), tokenA).andExpect(status().isOk());
        String url = "/api/users/" + userA.getId() + "/followings";

        // ユーザー名の一部（大文字小文字を区別しない）
        perform(get(url).param("q", "LOW_C"), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].id").value(userC.getId()));
        // 表示名の一部（userB の表示名は「びー」）
        perform(get(url).param("q", " び "), tokenA)
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].id").value(userB.getId()));
        // % や _ は文字として扱う（すべてに一致させない）
        perform(get(url).param("q", "%"), tokenA).andExpect(jsonPath("$.users", hasSize(0)));
        perform(get(url).param("q", "w_b"), tokenA).andExpect(jsonPath("$.users", hasSize(1)));
        perform(get(url).param("q", "wxb"), tokenA).andExpect(jsonPath("$.users", hasSize(0)));
        // 空なら絞り込まない
        perform(get(url).param("q", ""), tokenA).andExpect(jsonPath("$.users", hasSize(2)));

        perform(get("/api/users/" + userB.getId() + "/followers").param("q", "follow_a"), tokenA)
                .andExpect(jsonPath("$.users", hasSize(1)));
        perform(get("/api/users/" + userB.getId() + "/followers").param("q", "follow_c"), tokenA)
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.users", hasSize(0)));
    }

    @Test
    void 検索する文字が長すぎる場合は400() throws Exception {
        perform(get("/api/users/" + userA.getId() + "/followings").param("q", "a".repeat(51)), tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").exists());
    }

    @Test
    void 一覧はページに分けて返す() throws Exception {
        perform(post(followUrl(userB)), tokenA).andExpect(status().isOk());
        perform(post(followUrl(userC)), tokenA).andExpect(status().isOk());

        perform(get("/api/users/" + userA.getId() + "/followings").param("size", "1"), tokenA)
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
        perform(get("/api/users/" + userA.getId() + "/followings").param("size", "1").param("page", "1"), tokenA)
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private static String followUrl(User user) {
        return "/api/users/" + user.getId() + "/follow";
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM follows", Integer.class);
    }

    /** 解除済みの行を、指定した分数だけ前に解除したことにする */
    private void setUnfollowedMinutesAgo(int minutes) {
        jdbcTemplate.update("UPDATE follows SET unfollowed_at = ? WHERE unfollowed_at IS NOT NULL",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutes)));
    }

    private void setFollowedAt(User followee, LocalDateTime followedAt) {
        jdbcTemplate.update("UPDATE follows SET followed_at = ? WHERE followee_id = ?",
                Timestamp.valueOf(followedAt), followee.getId());
    }
}
