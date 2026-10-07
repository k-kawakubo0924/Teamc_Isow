package com.teamc.isow.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.reaction.PostLike;
import com.teamc.isow.backend.reaction.PostLikeRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
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
 * 投稿の検索（GET /api/search/posts。docs/search.md）の確認。
 * 投稿・いいねは Repository で直接作る（画像ファイルは使わないため、URL だけを持たせる）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchApiTest {

    static final String ME_EMAIL = "search-api-me@example.com";
    static final String EMAIL_20S = "search-api-20s@example.com";
    static final String EMAIL_40S = "search-api-40s@example.com";
    static final String EMAIL_NO_AGE = "search-api-no-age@example.com";

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
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User author20s;
    private User author40s;
    private User authorNoAge;
    private String token;
    private FashionCategory categoryA;
    private FashionCategory categoryB;
    /** 投稿にはタグが1つ以上必要なため、タグを指定しない投稿に付ける（どのテストのキーワードにも一致しない名前） */
    private Tag defaultTag;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(ME_EMAIL, "09000000050", "hash", "search_me"));
        author20s = userRepository.save(new User(EMAIL_20S, "09000000051", "hash", "search_20s"));
        author40s = userRepository.save(new User(EMAIL_40S, "09000000052", "hash", "search_40s"));
        authorNoAge = userRepository.save(new User(EMAIL_NO_AGE, "09000000053", "hash", "search_no_age"));
        setAgeGroup(author20s, AgeGroup.EARLY_20S);
        setAgeGroup(author40s, AgeGroup.FORTIES);
        token = token(me.getId());
        List<FashionCategory> categories = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        categoryA = categories.get(0);
        categoryB = categories.get(1);
        defaultTag = tagRepository.save(Tag.userInput("検索テスト用"));
    }

    @AfterEach
    void tearDown() {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        // 投稿を消すと likes は DB の連鎖削除で消える。search_histories・posts はユーザーを参照しているため先に消す
        jdbcTemplate.update("DELETE FROM search_histories");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM tags WHERE is_official = false");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?, ?)", ME_EMAIL, EMAIL_20S, EMAIL_40S, EMAIL_NO_AGE);
    }

    // ---- 条件 ----

    @Test
    void 条件をすべて省略すると全投稿を返し_履歴は記録しない() throws Exception {
        Post post1 = save(author20s, "コート", categoryA, "説明");
        Post post2 = save(author40s, "ニット", categoryB, "説明");

        search()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[*].id", contains(post2.getId().intValue(), post1.getId().intValue())))
                .andExpect(jsonPath("$.totalCount").value(2));

        assertThat(historyKeywords()).isEmpty();
    }

    @Test
    void キーワードは題名_投稿説明_タグのどれかに部分一致すれば対象_大文字小文字と全角半角を区別しない() throws Exception {
        Tag tag = tagRepository.save(Tag.userInput("Y2Kスタイル"));
        Post byTitle = save(author20s, "y2k コーデ", categoryA, "説明");
        Post byDescription = save(author20s, "題名", categoryA, "今日はY2K風にまとめました");
        Post byTag = save(author20s, "題名", categoryA, "説明", tag);
        save(author20s, "関係ない投稿", categoryA, "説明");

        search("q", "Ｙ２Ｋ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[*].id", contains(
                        byTag.getId().intValue(), byDescription.getId().intValue(), byTitle.getId().intValue())))
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    void キーワードの記号は文字として扱う() throws Exception {
        save(author20s, "コート", categoryA, "説明");
        Post percent = save(author20s, "100%ウール", categoryA, "説明");

        search("q", "%")
                .andExpect(jsonPath("$.posts[*].id", contains(percent.getId().intValue())));
        search("q", "_")
                .andExpect(jsonPath("$.posts", empty()));
    }

    @Test
    void ファッションの種類で絞り込む() throws Exception {
        Post a = save(author20s, "コート", categoryA, "説明");
        save(author20s, "コート", categoryB, "説明");

        search("categoryId", String.valueOf(categoryA.getId()))
                .andExpect(jsonPath("$.posts[*].id", contains(a.getId().intValue())))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 年代は投稿者のプロフィールの年代で絞り込み_年代が未設定の投稿者は出ない() throws Exception {
        Post post20s = save(author20s, "コート", categoryA, "説明");
        save(author40s, "コート", categoryA, "説明");
        save(authorNoAge, "コート", categoryA, "説明");

        search("ageGroup", "EARLY_20S")
                .andExpect(jsonPath("$.posts[*].id", contains(post20s.getId().intValue())));
    }

    @Test
    void 条件を組み合わせると_すべてを満たす投稿だけを返す() throws Exception {
        Post match = save(author20s, "ロングコート", categoryA, "説明");
        save(author20s, "ロングコート", categoryB, "説明");
        save(author40s, "ロングコート", categoryA, "説明");
        save(author20s, "ニット", categoryA, "説明");

        search("q", "コート", "categoryId", String.valueOf(categoryA.getId()), "ageGroup", "EARLY_20S")
                .andExpect(jsonPath("$.posts[*].id", contains(match.getId().intValue())))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void キーワードなしでジャンルと年代を組み合わせると_両方に合う投稿だけを返す() throws Exception {
        Post match = save(author20s, "投稿", categoryA, "説明");
        // ジャンルだけ合う・年代だけ合う・年代が未設定の投稿者
        save(author40s, "投稿", categoryA, "説明");
        save(author20s, "投稿", categoryB, "説明");
        save(authorNoAge, "投稿", categoryA, "説明");

        search("categoryId", String.valueOf(categoryA.getId()), "ageGroup", "EARLY_20S")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[*].id", contains(match.getId().intValue())))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void タグには半角カナ_全角英数字_大文字小文字の表記ゆれを吸収して一致する() throws Exception {
        Post casual = save(author20s, "題名", categoryA, "説明", tagRepository.save(Tag.userInput("カジュアルY2K")));
        save(author20s, "題名", categoryA, "説明");

        // 題名・説明には含まれない語で、タグだけに一致させる
        search("q", "ｶｼﾞｭｱﾙ").andExpect(jsonPath("$.posts[*].id", contains(casual.getId().intValue())));
        search("q", "ｙ２ｋ").andExpect(jsonPath("$.posts[*].id", contains(casual.getId().intValue())));
        search("q", "カジュアルy2k").andExpect(jsonPath("$.posts[*].id", contains(casual.getId().intValue())));
    }

    // ---- 並び順・ページ ----

    @Test
    void いいね数の多い順_同じなら新しい順に並ぶ() throws Exception {
        Post oldNoLike = save(author20s, "コート", categoryA, "説明");
        Post oneLike = save(author20s, "コート", categoryA, "説明");
        Post twoLikes = save(author20s, "コート", categoryA, "説明");
        Post newNoLike = save(author20s, "コート", categoryA, "説明");
        likeRepository.save(new PostLike(me, oneLike));
        likeRepository.save(new PostLike(me, twoLikes));
        likeRepository.save(new PostLike(author40s, twoLikes));

        search("q", "コート")
                .andExpect(jsonPath("$.posts[*].id", contains(twoLikes.getId().intValue(), oneLike.getId().intValue(),
                        newNoLike.getId().intValue(), oldNoLike.getId().intValue())))
                .andExpect(jsonPath("$.posts[0].likeCount").value(2))
                .andExpect(jsonPath("$.posts[0].likedByMe").value(true));
    }

    @Test
    void ページで区切り_件数は条件に一致した全件() throws Exception {
        for (int i = 0; i < 5; i++) {
            save(author20s, "コート" + i, categoryA, "説明");
        }

        search("q", "コート", "page", "0", "size", "2")
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalCount").value(5));
        search("q", "コート", "page", "2", "size", "2")
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(5));
    }

    // ---- 検索履歴 ----

    @Test
    void キーワードがあれば整えた形で履歴に記録し_条件だけの検索は記録しない() throws Exception {
        search("q", "　Ｙ２Ｋ  コーデ ").andExpect(status().isOk());
        search("categoryId", String.valueOf(categoryA.getId()), "ageGroup", "FORTIES").andExpect(status().isOk());
        search("q", "  ", "ageGroup", "FORTIES").andExpect(status().isOk());

        assertThat(historyKeywords()).containsExactly("Y2K コーデ");
    }

    @Test
    void 一致する投稿がなくても履歴に記録する() throws Exception {
        search("q", "存在しない語")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", empty()))
                .andExpect(jsonPath("$.totalCount").value(0));

        assertThat(historyKeywords()).containsExactly("存在しない語");
    }

    // ---- 入力チェック ----

    @Test
    void キーワードは100文字まで_超えると400で履歴にも記録しない() throws Exception {
        String max = "あ".repeat(SearchHistory.MAX_KEYWORD_LENGTH);
        search("q", max).andExpect(status().isOk());
        // 前後の空白は数えない
        search("q", " " + max + " ").andExpect(status().isOk());

        search("q", max + "い")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").value("キーワードは100文字以内で入力してください"));

        assertThat(historyKeywords()).containsExactly(max);
    }

    @Test
    void 存在しない選択肢は400で_項目ごとにエラーを返す() throws Exception {
        search("categoryId", "abc", "ageGroup", "TWENTIES")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").value("ファッションの種類の選択肢が正しくありません"))
                .andExpect(jsonPath("$.errors.ageGroup").value("年代の選択肢が正しくありません"));
        search("categoryId", "999999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").exists());
    }

    @Test
    void 未ログインは401() throws Exception {
        mockMvc.perform(get("/api/search/posts").param("q", "コート")).andExpect(status().isUnauthorized());

        assertThat(historyKeywords()).isEmpty();
    }

    // ---- 部品 ----

    /** @param params 項目名, 値, 項目名, 値, ... */
    private ResultActions search(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/search/posts").header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private Post save(User author, String title, FashionCategory category, String description, Tag... tags) {
        return postRepository.save(new Post(author, title, category, null, description, null,
                List.of("http://localhost/uploads/search.jpg"), tags.length == 0 ? List.of(defaultTag) : List.of(tags)));
    }

    private void setAgeGroup(User user, AgeGroup ageGroup) {
        jdbcTemplate.update("UPDATE users SET age_group = ? WHERE id = ?", ageGroup.name(), user.getId());
    }

    private List<String> historyKeywords() {
        return jdbcTemplate.queryForList(
                "SELECT keyword FROM search_histories ORDER BY searched_at DESC, id DESC", String.class);
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
