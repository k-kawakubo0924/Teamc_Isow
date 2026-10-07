package com.teamc.isow.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.follow.Follow;
import com.teamc.isow.backend.follow.FollowRepository;
import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ユーザーの検索・検索履歴・候補ワード（GET /api/search/users ほか。docs/search.md）の確認。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchUserHistorySuggestionApiTest {

    static final String ME_EMAIL = "search-uh-me@example.com";
    static final String OTHER_EMAIL = "search-uh-other@example.com";
    static final String HARU_EMAIL = "search-uh-haru@example.com";
    static final String HARUKA_EMAIL = "search-uh-haruka@example.com";
    static final String CHIHARU_EMAIL = "search-uh-chiharu@example.com";

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
    private TagRepository tagRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private SearchHistoryService searchHistoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User other;
    private String token;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(ME_EMAIL, "09000000070", "hash", "uh_me"));
        other = userRepository.save(new User(OTHER_EMAIL, "09000000071", "hash", "uh_other"));
        token = token(me.getId());
    }

    @AfterEach
    void tearDown() {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        // 投稿を消すと likes は DB の連鎖削除で消える。履歴・フォロー・投稿はユーザーを参照しているため先に消す
        jdbcTemplate.update("DELETE FROM search_histories");
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM tags WHERE is_official = false");
        jdbcTemplate.update("UPDATE tags SET is_active = true");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?, ?, ?)",
                ME_EMAIL, OTHER_EMAIL, HARU_EMAIL, HARUKA_EMAIL, CHIHARU_EMAIL);
    }

    // ---- ユーザーの検索 ----

    @Test
    void ユーザー名か表示名の部分一致で探し_自分は含めない_項目とフォロー済みかを返す() throws Exception {
        User haru = userRepository.save(new User(HARU_EMAIL, "09000000072", "hash", "haru_st"));
        User byDisplayName = userRepository.save(new User(HARUKA_EMAIL, "09000000073", "hash", "uh_kaori"));
        jdbcTemplate.update("UPDATE users SET display_name = 'HARUKA', height_cm = 158, gender = 'FEMALE', "
                + "profile_image_url = 'http://localhost/uploads/k.jpg' WHERE id = ?", byDisplayName.getId());
        jdbcTemplate.update("UPDATE users SET display_name = 'haru' WHERE id = ?", me.getId());
        followRepository.save(new Follow(me, haru));

        searchUsers("q", "HaRu")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[*].id", contains(haru.getId().intValue(), byDisplayName.getId().intValue())))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.users[0].username").value("haru_st"))
                .andExpect(jsonPath("$.users[0].displayName").value("haru_st"))
                .andExpect(jsonPath("$.users[0].heightCm").value(nullValue()))
                .andExpect(jsonPath("$.users[0].gender").value(nullValue()))
                .andExpect(jsonPath("$.users[0].followingByMe").value(true))
                .andExpect(jsonPath("$.users[1].displayName").value("HARUKA"))
                .andExpect(jsonPath("$.users[1].profileImageUrl").value("http://localhost/uploads/k.jpg"))
                .andExpect(jsonPath("$.users[1].heightCm").value(158))
                .andExpect(jsonPath("$.users[1].gender").value("FEMALE"))
                .andExpect(jsonPath("$.users[1].followingByMe").value(false))
                // メールアドレスなどの個人情報は返さない
                .andExpect(jsonPath("$.users[0].email").doesNotExist())
                .andExpect(jsonPath("$.users[0].phoneNumber").doesNotExist());
    }

    @Test
    void 完全一致_前方一致_部分一致_ユーザー名の順に並ぶ() throws Exception {
        User chiharu = userRepository.save(new User(CHIHARU_EMAIL, "09000000074", "hash", "chiharu"));
        User haruka = userRepository.save(new User(HARUKA_EMAIL, "09000000073", "hash", "haruka"));
        User haru = userRepository.save(new User(HARU_EMAIL, "09000000072", "hash", "haru"));

        searchUsers("q", "haru")
                .andExpect(jsonPath("$.users[*].id", contains(
                        haru.getId().intValue(), haruka.getId().intValue(), chiharu.getId().intValue())));
    }

    @Test
    void 解除済みのフォローはフォロー済みにしない() throws Exception {
        followRepository.save(new Follow(me, other));
        jdbcTemplate.update("UPDATE follows SET unfollowed_at = CURRENT_TIMESTAMP, active = NULL");

        searchUsers("q", "uh_other")
                .andExpect(jsonPath("$.users[0].followingByMe").value(false));
    }

    @Test
    void キーワードの記号は文字として扱う() throws Exception {
        // ワイルドカードとして扱うと uh_other に一致してしまう語
        searchUsers("q", "%").andExpect(jsonPath("$.users", empty()));
        searchUsers("q", "u%r").andExpect(jsonPath("$.users", empty()));
        searchUsers("q", "o_h").andExpect(jsonPath("$.users", empty()));
        // 文字としての _ には一致する
        searchUsers("q", "h_o").andExpect(jsonPath("$.users[*].id", contains(other.getId().intValue())));
    }

    @Test
    void キーワードがなければ自分以外の全ユーザーで_履歴は記録しない() throws Exception {
        searchUsers()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[*].id", contains(other.getId().intValue())));
        searchUsers("q", "  ").andExpect(jsonPath("$.totalCount").value(1));

        assertThat(historyKeywords(me)).isEmpty();
    }

    @Test
    void ユーザーの検索もキーワードがあれば履歴に記録し_100文字を超えたら400() throws Exception {
        searchUsers("q", " uh_OTHER ").andExpect(status().isOk());
        searchUsers("q", "a".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").value("キーワードは100文字以内で入力してください"));

        assertThat(historyKeywords(me)).containsExactly("uh_OTHER");
    }

    // ---- 検索履歴 ----

    @Test
    void 自分の履歴だけを新しい順に返す() throws Exception {
        searchHistoryService.record(me.getId(), "古着");
        searchHistoryService.record(other.getId(), "他人の語");
        searchHistoryService.record(me.getId(), "Y2K");

        getWithToken("/api/search/history")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories[*].keyword", contains("Y2K", "古着")))
                .andExpect(jsonPath("$.histories[0].id").isNumber())
                .andExpect(jsonPath("$.histories[0].searchedAt").isNotEmpty());
    }

    @Test
    void 自分の履歴を1件削除できる_他人の履歴は削除されないが同じ204を返す() throws Exception {
        searchHistoryService.record(me.getId(), "古着");
        searchHistoryService.record(me.getId(), "Y2K");
        searchHistoryService.record(other.getId(), "他人の語");
        Long mine = historyId(me, "古着");
        Long others = historyId(other, "他人の語");

        mockMvc.perform(withToken(delete("/api/search/history/" + mine))).andExpect(status().isNoContent());
        // 2回目（連打）・他人の履歴・存在しない ID も 204
        mockMvc.perform(withToken(delete("/api/search/history/" + mine))).andExpect(status().isNoContent());
        mockMvc.perform(withToken(delete("/api/search/history/" + others))).andExpect(status().isNoContent());
        mockMvc.perform(withToken(delete("/api/search/history/999999"))).andExpect(status().isNoContent());

        assertThat(historyKeywords(me)).containsExactly("Y2K");
        assertThat(historyKeywords(other)).containsExactly("他人の語");
    }

    @Test
    void 自分の履歴をすべて削除できる_他人の履歴は残る() throws Exception {
        searchHistoryService.record(me.getId(), "古着");
        searchHistoryService.record(me.getId(), "Y2K");
        searchHistoryService.record(other.getId(), "他人の語");

        mockMvc.perform(withToken(delete("/api/search/history"))).andExpect(status().isNoContent());

        assertThat(historyKeywords(me)).isEmpty();
        assertThat(historyKeywords(other)).containsExactly("他人の語");
        getWithToken("/api/search/history").andExpect(jsonPath("$.histories", empty()));
    }

    @Test
    void 投稿とユーザーの検索で同じキーワードを表記ゆれ込みで繰り返しても_履歴は1件で最後の入力の形になる() throws Exception {
        mockMvc.perform(withToken(get("/api/search/posts")).param("q", "Y2K")).andExpect(status().isOk());
        searchUsers("q", "ｙ２ｋ").andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/search/posts")).param("q", " y2k ")).andExpect(status().isOk());

        getWithToken("/api/search/history")
                .andExpect(jsonPath("$.histories", hasSize(1)))
                .andExpect(jsonPath("$.histories[0].keyword").value("y2k"));
    }

    @Test
    void 検索APIで上限を超えて検索すると_古い履歴から消える() throws Exception {
        for (int i = 1; i <= SearchHistoryService.MAX_PER_USER + 2; i++) {
            mockMvc.perform(withToken(get("/api/search/posts")).param("q", "語" + i)).andExpect(status().isOk());
        }

        List<String> expected = new ArrayList<>();
        for (int i = SearchHistoryService.MAX_PER_USER + 2; i >= 3; i--) {
            expected.add("語" + i);
        }
        getWithToken("/api/search/history")
                .andExpect(jsonPath("$.histories", hasSize(SearchHistoryService.MAX_PER_USER)))
                .andExpect(jsonPath("$.histories[*].keyword", contains(expected.toArray())));
    }

    // ---- 候補ワード ----

    @Test
    void その種類の投稿でよく使われているタグを多い順に返し_足りない分を公式タグで補う() throws Exception {
        List<FashionCategory> categories = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        FashionCategory categoryA = categories.get(0);
        FashionCategory categoryB = categories.get(1);
        List<Tag> officials = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc();
        Tag tagA = tagRepository.save(Tag.userInput("候補A"));
        Tag tagB = tagRepository.save(Tag.userInput("候補B"));
        Tag otherCategoryTag = tagRepository.save(Tag.userInput("別の種類"));
        Tag inactive = tagRepository.save(Tag.userInput("非表示"));
        savePost(categoryA, tagA, tagB, inactive);
        savePost(categoryA, tagA, tagB, inactive);
        savePost(categoryA, tagA, officials.get(0), inactive);
        savePost(categoryB, otherCategoryTag);
        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE id = ?", inactive.getId());

        // よく使われている順（候補A 3件 → 候補B 2件 → 公式タグ0 1件）→ 公式タグ0 を除いた公式タグを表示順に、10件まで
        List<String> expected = new ArrayList<>(List.of("候補A", "候補B", officials.get(0).getName()));
        for (int i = 1; expected.size() < SearchService.MAX_SUGGESTIONS; i++) {
            expected.add(officials.get(i).getName());
        }
        getWithToken("/api/search/suggestions?categoryId=" + categoryA.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.words", hasSize(SearchService.MAX_SUGGESTIONS)))
                .andExpect(jsonPath("$.words[*].name", contains(expected.toArray())))
                .andExpect(jsonPath("$.words[0].source").value("POPULAR"))
                .andExpect(jsonPath("$.words[2].source").value("POPULAR"))
                .andExpect(jsonPath("$.words[3].source").value("OFFICIAL"));
    }

    @Test
    void 種類を省略すると全投稿で数える() throws Exception {
        List<FashionCategory> categories = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        Tag tagA = tagRepository.save(Tag.userInput("候補A"));
        Tag otherCategoryTag = tagRepository.save(Tag.userInput("別の種類"));
        savePost(categories.get(0), tagA);
        savePost(categories.get(1), otherCategoryTag);
        savePost(categories.get(1), otherCategoryTag);

        getWithToken("/api/search/suggestions")
                .andExpect(jsonPath("$.words[0].name").value("別の種類"))
                .andExpect(jsonPath("$.words[1].name").value("候補A"));
    }

    @Test
    void 投稿がなければ公式タグだけを返す() throws Exception {
        FashionCategory category = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
        List<Tag> officials = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc();

        getWithToken("/api/search/suggestions?categoryId=" + category.getId())
                .andExpect(jsonPath("$.words", hasSize(Math.min(officials.size(), SearchService.MAX_SUGGESTIONS))))
                .andExpect(jsonPath("$.words[0].name").value(officials.get(0).getName()))
                .andExpect(jsonPath("$.words[0].source").value("OFFICIAL"));
    }

    @Test
    void 存在しない種類は400() throws Exception {
        getWithToken("/api/search/suggestions?categoryId=999999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").value("ファッションの種類の選択肢が正しくありません"));
    }

    // ---- 認証 ----

    @Test
    void 未ログインはすべて401() throws Exception {
        searchHistoryService.record(me.getId(), "古着");

        mockMvc.perform(get("/api/search/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/search/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/search/history/" + historyId(me, "古着"))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/search/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/search/suggestions")).andExpect(status().isUnauthorized());

        assertThat(historyKeywords(me)).containsExactly("古着");
    }

    // ---- 部品 ----

    /** @param params 項目名, 値, 項目名, 値, ... */
    private ResultActions searchUsers(String... params) throws Exception {
        MockHttpServletRequestBuilder request = withToken(get("/api/search/users"));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions getWithToken(String url) throws Exception {
        return mockMvc.perform(withToken(get(url)));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private void savePost(FashionCategory category, Tag... tags) {
        postRepository.save(new Post(other, "題名", category, null, "説明", null,
                List.of("http://localhost/uploads/suggest.jpg"), List.of(tags)));
    }

    private List<String> historyKeywords(User user) {
        return jdbcTemplate.queryForList(
                "SELECT keyword FROM search_histories WHERE user_id = ? ORDER BY searched_at DESC, id DESC",
                String.class, user.getId());
    }

    private Long historyId(User user, String keyword) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM search_histories WHERE user_id = ? AND keyword = ?", Long.class, user.getId(), keyword);
    }

    private String token(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
