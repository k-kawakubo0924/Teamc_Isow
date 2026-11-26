package com.teamc.isow.backend.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.image.ImageTestSupport;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ブロックの「条件の書き忘れ」を防ぐためのテスト（docs/profile.md「ブロック」の「条件の書き忘れを防ぐ仕組み」）。
 *
 * <ol>
 *   <li><b>API の棚卸し</b>：アプリにあるすべての利用者向け API を、下の {@link #REGISTRY} に登録しているかを確かめる。
 *       API を新しく作ったら、ここに登録しないとこのテストが失敗する。登録するときに、ブロックした相手が漏れないかを必ず考えること</li>
 *   <li><b>漏れの確認</b>：A が B をブロックした状態で、登録した GET の API を A と B のそれぞれで呼び、
 *       相手のユーザー名・投稿の題名が応答に1つも含まれていないことを確かめる</li>
 * </ol>
 *
 * <p>GET 以外の API は、1人の相手を対象にする操作として BlockApiTest で確かめるもの、
 * 自分のデータだけを扱うもの、などの理由を書いて登録する
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisibilityInventoryTest {

    /** 登録の種類 */
    private enum Kind {
        /** A と B の両方で呼び、相手が応答に含まれないことを確かめる（GET のみ） */
        CHECKED,
        /** 1人の相手（ユーザー・投稿）を対象にする操作。ブロックの間柄なら 404 にすることを BlockApiTest で確かめる */
        TARGET_CHECKED_IN_BLOCK_API_TEST,
        /** 自分のデータだけを扱う、または他人が関わらない */
        OWN_DATA_ONLY,
        /** 確かめない理由がある（理由を必ず書く） */
        EXEMPT
    }

    /**
     * ミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」）での扱い。CHECKED の GET の API には必ず書く
     * （書かないと「すべての利用者向けAPIが登録されていて…」のテストが失敗する）。
     * 確かめる場面は「A が C をミュートした」。C は {c}・{postC} の持ち主
     */
    private enum Mute {
        /** 流れてくる一覧。ミュートの前は C の投稿が出ていて、後は出ない */
        HIDES,
        /** 自分から探しに行く場所・自分で選んだもの・人の一覧。ミュートの前も後も C が出ている */
        SHOWS,
        /** C が出てくることのない API（自分のデータ・マスタ・件数だけ・DM など） */
        NOT_RELATED
    }

    /**
     * 登録の内容。path は CHECKED のときに呼ぶ URL（{a}・{b}・{c}・{postC} などは下の render で置き換える）。
     * mutePath は、ミュートの確かめで path の代わりに呼ぶ URL（path では C が出てこない API のため。null なら path）
     */
    private record Entry(Kind kind, String pathOrReason, Mute mute, String mutePath) {

        String pathForMute() {
            return mutePath != null ? mutePath : pathOrReason;
        }
    }

    /**
     * すべての利用者向け API の登録（キーは「メソッド パス」。パスの変数は {名前} の形）。
     * 管理者向けの API（/api/admin/**）は利用者のブロックと関係がないため対象外
     */
    private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

    static {
        // ---- ログイン・共通 ----
        own("POST /api/auth/register", "ログイン前の操作");
        own("POST /api/auth/login", "ログイン前の操作");
        own("POST /api/auth/password-reset/request", "ログイン前の操作。登録の有無にかかわらず同じ応答（PasswordResetApiTest）");
        own("POST /api/auth/password-reset/confirm", "ログイン前の操作。再設定の鍵で本人を確かめる（PasswordResetApiTest）");
        checked("GET /api/auth/me", "/api/auth/me", Mute.NOT_RELATED);
        checked("GET /api/health", "/api/health", Mute.NOT_RELATED);
        checked("GET /api/masters", "/api/masters", Mute.NOT_RELATED);
        checked("GET /api/tags", "/api/tags?q=" + "古", Mute.NOT_RELATED);

        // ---- お知らせ・通知（通知はブロックした時点で2人の間のものを消す） ----
        checked("GET /api/announcements", "/api/announcements", Mute.NOT_RELATED);
        own("POST /api/announcements/{id}/read", "お知らせは全員に同じもので、他人が関わらない");
        // 通知はミュートで変えない（docs/profile.md「ミュート（投稿の非表示）」の「通知」。案A）
        checked("GET /api/notifications", "/api/notifications", Mute.SHOWS);
        checked("GET /api/notifications/summary", "/api/notifications/summary", Mute.NOT_RELATED);
        own("POST /api/notifications/{id}/read", "自分の通知だけを扱う");
        own("POST /api/notifications/{id}/unread", "自分の通知だけを扱う");
        own("DELETE /api/notifications/{id}", "自分の通知だけを扱う");
        own("POST /api/notifications/read-all", "自分の通知だけを扱う");

        // ---- 投稿 ----
        own("POST /api/posts", "自分の投稿を作る");
        checked("GET /api/posts", "/api/posts?tab=latest", Mute.HIDES);
        // フォローしていても隠す（フォローは外さない）
        checked("GET /api/posts#following", "/api/posts?tab=following", Mute.HIDES);
        checked("GET /api/posts#recommended", "/api/posts?tab=recommended", Mute.HIDES);
        checked("GET /api/posts/me", "/api/posts/me", Mute.NOT_RELATED);
        // 自分から探しに行った投稿（URL を直接開いた場合も）は見える
        checked("GET /api/posts/{id}", "/api/posts/{postC}", Mute.SHOWS);
        // 見えてはいけない投稿の詳細を、その投稿の ID で直接開く（一覧に出ないだけでなく、ID を知っていても開けないこと）。
        // 上の {postC}（見えてよい投稿）だけでは、詳細の確かめを外しても気づけなかった（2026-10-28、わざと外して確かめた）
        // ブロック・措置の確かめ専用（ミュートの投稿は上の GET /api/posts/{id} で確かめる）
        checked("GET /api/posts/{id}#forbidden", "/api/posts/{forbiddenPost}", Mute.NOT_RELATED);
        // 自分の投稿だけを編集・削除できる。ブロックの間柄にある人の投稿は 404、見えるが他人の投稿は 403
        target("PUT /api/posts/{id}");
        target("DELETE /api/posts/{id}");
        target("POST /api/posts/{postId}/like");
        target("DELETE /api/posts/{postId}/like");
        target("POST /api/posts/{postId}/favorite");
        target("DELETE /api/posts/{postId}/favorite");

        // ---- プロフィール・フォロー ----
        checked("GET /api/users/me", "/api/users/me", Mute.NOT_RELATED);
        own("PUT /api/users/me", "自分のプロフィールを変える");
        own("POST /api/users/me/profile-image", "自分のプロフィール画像を変える");
        own("POST /api/users/me/email-change", "自分のメールアドレスの変更を申し込む（EmailChangeApiTest）");
        own("POST /api/users/me/password", "自分のパスワードを変える（PasswordChangeApiTest）");
        own("POST /api/auth/email-change/confirm", "確認のリンクの鍵で本人を確かめる。ログインしていない端末でも使える（EmailChangeApiTest）");
        // お気に入りは自分で選んだもので、流れてくる一覧ではないため隠さない
        checked("GET /api/users/me/favorites", "/api/users/me/favorites", Mute.SHOWS);
        checked("GET /api/users/{userId}", "/api/users/{c}", Mute.SHOWS);
        checked("GET /api/users/{userId}/posts", "/api/users/{c}/posts", Mute.SHOWS);
        // C の「フォロー中の投稿」には C 自身の投稿は出ないため、C をフォローしている B の「フォロー中の投稿」で確かめる
        checked("GET /api/users/{userId}/following-posts", "/api/users/{c}/following-posts?seed=7", Mute.HIDES,
                "/api/users/{b}/following-posts?seed=7");
        // C の一覧には C 自身が出ないため、C が出る A の一覧で確かめる（人の一覧で、投稿ではないため隠さない）
        checked("GET /api/users/{userId}/followings", "/api/users/{c}/followings", Mute.SHOWS,
                "/api/users/{a}/followings");
        checked("GET /api/users/{userId}/followers", "/api/users/{c}/followers", Mute.SHOWS,
                "/api/users/{a}/followers");
        target("POST /api/users/{userId}/follow");
        target("DELETE /api/users/{userId}/follow");

        // ---- ブロック ----
        target("POST /api/users/{userId}/block");
        own("DELETE /api/users/{userId}/block", "自分がしたブロックを解除するだけ");
        own("GET /api/blocks", "自分がブロックした人の一覧（ブロックした相手を出すための画面）");

        // ---- ミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」） ----
        // ブロックの間柄・利用停止中の相手は 404 にすることは、BlockApiTest ではなく MuteApiTest で確かめている
        target("POST /api/users/{userId}/mute");
        own("DELETE /api/users/{userId}/mute", "自分がしたミュートを解除するだけ");
        own("GET /api/mutes", "自分がミュートした人の一覧。ブロックの間柄・利用停止中の相手は出さない（MuteApiTest）");

        // ---- 通報（docs/admin.md「通報」） ----
        target("POST /api/reports");

        // ---- お問い合わせ（docs/settings.md「お問い合わせ」） ----
        own("POST /api/contacts", "運営に宛てて送るだけで、他人のデータを返さない");
        own("POST /api/contacts/appeal", "利用停止中の本人が運営に宛てて送るだけ。トークンではなく申し立て用の鍵で本人を確かめる");

        // ---- 検索 ----
        checked("GET /api/search/posts", "/api/search/posts?q=BLKPOST", Mute.HIDES);
        // その人にたどり着けないと解除しにくいため、ユーザー検索には出す
        checked("GET /api/search/users", "/api/search/users?q=blkuser", Mute.SHOWS);
        checked("GET /api/search/history", "/api/search/history", Mute.NOT_RELATED);
        own("DELETE /api/search/history/{id}", "自分の検索履歴だけを扱う");
        own("DELETE /api/search/history", "自分の検索履歴だけを扱う");
        checked("GET /api/search/suggestions", "/api/search/suggestions", Mute.NOT_RELATED);

        // ---- 相談（DM） ----
        target("POST /api/conversations");
        target("GET /api/users/{userId}/consultation-status");
        target("GET /api/users/consultation-statuses");
        // DM・相談はミュートで変えない
        checked("GET /api/conversations/summary", "/api/conversations/summary", Mute.NOT_RELATED);
        exempt("GET /api/conversations", "ブロックした時点で相談は終了するが、過去の会話は両方から読めるまま残す（docs/dm.md「ブロック」）");
        exempt("GET /api/conversations/{conversationId}", "過去の会話は両方から読めるまま残す（docs/dm.md「ブロック」）");
        exempt("GET /api/conversations/{conversationId}/messages", "過去の会話は両方から読めるまま残す（docs/dm.md「ブロック」）");
        exempt("GET /api/conversations/{conversationId}/messages/{messageId}/image",
                "会話の当事者2人にだけ配る。過去の会話は両方から読めるまま残すため、画像も同じ（MessageImageApiTest）");
        own("POST /api/conversations/{conversationId}/accept", "会話の当事者だけ。ブロックした時点で申請中の相談は終了しているため承認できない");
        own("POST /api/conversations/{conversationId}/reject", "会話の当事者だけ。ブロックした時点で申請中の相談は終了しているため拒否できない");
        own("POST /api/conversations/{conversationId}/end", "会話の当事者だけ。ブロックした時点で終了している");
        own("POST /api/conversations/{conversationId}/messages", "会話の当事者だけ。終了した会話には送れない（BlockApiTest）");
        own("POST /api/conversations/{conversationId}/pin", "会話の当事者だけ。自分の設定だけを扱い、相手には分からない（ConversationPinApiTest）");
        own("DELETE /api/conversations/{conversationId}/pin", "会話の当事者だけ。自分の設定だけを扱う");
        own("POST /api/conversations/{conversationId}/mute", "会話の当事者だけ。自分の設定だけを扱い、相手には分からない（ConversationMuteApiTest）");
        own("DELETE /api/conversations/{conversationId}/mute", "会話の当事者だけ。自分の設定だけを扱う");
    }

    private static void checked(String key, String path, Mute mute) {
        checked(key, path, mute, null);
    }

    private static void checked(String key, String path, Mute mute, String mutePath) {
        REGISTRY.put(key, new Entry(Kind.CHECKED, path, mute, mutePath));
    }

    private static void target(String key) {
        REGISTRY.put(key, new Entry(Kind.TARGET_CHECKED_IN_BLOCK_API_TEST, "BlockApiTest", null, null));
    }

    private static void own(String key, String reason) {
        REGISTRY.put(key, new Entry(Kind.OWN_DATA_ONLY, reason, null, null));
    }

    private static void exempt(String key, String reason) {
        REGISTRY.put(key, new Entry(Kind.EXEMPT, reason, null, null));
    }

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
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private BlockFixture f;
    private Post postOfA;
    private Post postOfB;
    private Post postOfC;
    /** 見る人（ユーザーの ID）ごとの、見えてはいけない投稿（{forbiddenPost} の置き換え先。テストごとに決める） */
    private final Map<Long, Post> forbiddenPostFor = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        f = new BlockFixture(mockMvc, jwtEncoder, userRepository, postRepository, fashionCategoryRepository,
                tagRepository, jdbcTemplate);
        f.createUsers();
    }

    @AfterEach
    void tearDown() {
        f.cleanUp();
    }

    // ---- 1. API の棚卸し ----

    @Test
    void すべての利用者向けAPIが登録されていて_登録だけが残っているものもない() {
        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                String path = pattern.replaceAll("\\{(\\w+):[^}]*}", "{$1}");
                if (!path.startsWith("/api/") || path.startsWith("/api/admin/") || path.equals("/api/admin")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    actual.add(method + " " + path);
                }
            }
        }
        Set<String> registered = new TreeSet<>();
        REGISTRY.keySet().forEach(key -> registered.add(key.replaceAll("#.*$", "")));

        Set<String> missing = new TreeSet<>(actual);
        missing.removeAll(registered);
        Set<String> stale = new TreeSet<>(registered);
        stale.removeAll(actual);

        assertThat(missing)
                .as("登録していない API があります。VisibilityInventoryTest.REGISTRY に登録し、ブロックした相手が漏れないかを確かめてください")
                .isEmpty();
        assertThat(stale).as("もう存在しない API が登録に残っています").isEmpty();
        // 理由を書かずに確かめないものを作らない
        REGISTRY.forEach((key, entry) -> assertThat(entry.pathOrReason()).as(key).isNotBlank());
        // GET の API には、ミュートで隠すか（HIDES・SHOWS・NOT_RELATED）を必ず書く。流れてくる一覧を作ったときに、
        // ミュートの条件の付け忘れ・付けすぎを考えさせるため（docs/profile.md「ミュート（投稿の非表示）」）
        REGISTRY.forEach((key, entry) -> {
            if (entry.kind() == Kind.CHECKED) {
                assertThat(entry.mute()).as(key + " のミュートの扱い（HIDES・SHOWS・NOT_RELATED）を書いてください").isNotNull();
            }
        });
    }

    // ---- 2. 漏れの確認 ----

    @Test
    void ブロックの後_どのGETのAPIにも相手のユーザー名と投稿が出ない() throws Exception {
        prepareRelations();
        // 確かめる意味があるか：ブロックの前は、C の一覧・検索に A と B の両方が出ている
        assertThat(body(f.c, "/api/users/{c}/followers")).contains("blkuser_aaa", "blkuser_bbb");
        assertThat(body(f.a, "/api/search/posts?q=BLKPOST")).contains(ImageTestSupport.key("BLKPOSTBBB.jpg"));
        assertThat(body(f.b, "/api/search/users?q=blkuser")).contains("blkuser_aaa");

        // ブロックの前は、A は B の投稿の詳細を開ける（{forbiddenPost} の確かめが空振りしていないこと）
        assertThat(body(f.a, "/api/posts/" + postOfB.getId())).contains("BLKPOSTBBB");

        f.block(f.a, f.b).andExpect(status().isNoContent());
        forbiddenPostFor.put(f.a.getId(), postOfB);
        forbiddenPostFor.put(f.b.getId(), postOfA);

        List<String> leaks = new ArrayList<>();
        for (Map.Entry<String, Entry> registered : REGISTRY.entrySet()) {
            if (registered.getValue().kind() != Kind.CHECKED) {
                continue;
            }
            String path = registered.getValue().pathOrReason();
            leaks.addAll(findLeaks(registered.getKey(), f.a, path, "blkuser_bbb", "BLKPOSTBBB"));
            leaks.addAll(findLeaks(registered.getKey(), f.b, path, "blkuser_aaa", "BLKPOSTAAA"));
        }
        assertThat(leaks).as("ブロックした相手が応答に含まれている API").isEmpty();
    }

    // ---- 3. 措置（docs/admin.md「6. 措置」）：非公開にした投稿・利用停止中のユーザーも、同じ登録で漏れを確かめる ----

    @Test
    void 非公開にした投稿は_投稿者以外のどのGETのAPIにも出ず_投稿者の自分の一覧には出る() throws Exception {
        prepareRelations();
        Post hidden = f.createPost(f.c, "BLKHIDDENPOST");
        f.like(f.a, hidden);
        f.favorite(f.a, hidden);
        // 一覧のカード・通知の投稿は、題名を持たず、目印は、題名から作った写真のキー（サムネイルの URL）にしか出ない。
        // 非公開の投稿はサムネイルが null になる（写真を配らないため）ので、漏れても目印が出ない。そのため、カードの形
        // （{"id":…,"thumbnailUrl":…}。通知の投稿も同じ形）でも探す
        String card = "\"id\":" + hidden.getId() + ",\"thumbnailUrl\"";
        // 確かめる意味があるか：非公開にする前は、A の一覧・検索に出ている（カードの形でも見つかる）
        assertThat(body(f.a, "/api/search/posts?q=BLKHIDDEN")).contains(ImageTestSupport.key("BLKHIDDENPOST.jpg")).contains(card);
        assertThat(body(f.a, "/api/users/me/favorites")).contains(ImageTestSupport.key("BLKHIDDENPOST.jpg")).contains(card);

        hide(hidden);
        forbiddenPostFor.put(f.a.getId(), hidden);
        forbiddenPostFor.put(f.b.getId(), hidden);

        List<String> leaks = new ArrayList<>();
        for (Map.Entry<String, Entry> registered : REGISTRY.entrySet()) {
            if (registered.getValue().kind() == Kind.CHECKED) {
                String path = registered.getValue().pathOrReason();
                leaks.addAll(findLeaks(registered.getKey(), f.a, path, "BLKHIDDENPOST", card));
                leaks.addAll(findLeaks(registered.getKey(), f.b, path, "BLKHIDDENPOST", card));
            }
        }
        assertThat(leaks).as("非公開にした投稿が、投稿者以外への応答に含まれている API").isEmpty();
        // 投稿者本人は、自分の一覧で見られる
        assertThat(body(f.c, "/api/posts/me")).contains("BLKHIDDENPOST");
        assertThat(body(f.c, "/api/users/{c}/posts")).contains(card);
    }

    @Test
    void 利用停止中のユーザーは_ほかの人へのどのGETのAPIにも名前と投稿が出ない() throws Exception {
        prepareRelations();
        assertThat(body(f.c, "/api/users/{c}/followers")).contains("blkuser_bbb");
        assertThat(body(f.a, "/api/notifications")).contains("blkuser_bbb");

        suspend(f.b);
        forbiddenPostFor.put(f.a.getId(), postOfB);
        forbiddenPostFor.put(f.c.getId(), postOfB);

        List<String> leaks = new ArrayList<>();
        for (Map.Entry<String, Entry> registered : REGISTRY.entrySet()) {
            if (registered.getValue().kind() == Kind.CHECKED) {
                String path = registered.getValue().pathOrReason();
                leaks.addAll(findLeaks(registered.getKey(), f.a, path, "blkuser_bbb", "BLKPOSTBBB"));
                leaks.addAll(findLeaks(registered.getKey(), f.c, path, "blkuser_bbb", "BLKPOSTBBB"));
            }
        }
        assertThat(leaks).as("利用停止中のユーザーが、ほかの人への応答に含まれている API").isEmpty();
    }

    @Test
    void 利用停止中のユーザーのトークンでは_登録したすべてのAPIが403になる() throws Exception {
        suspend(f.b);

        List<String> notBlocked = new ArrayList<>();
        Set<String> keys = new TreeSet<>();
        REGISTRY.keySet().forEach(key -> keys.add(key.replaceAll("#.*$", "")));
        for (String key : keys) {
            if (PUBLIC_APIS.contains(key)) {
                continue;
            }
            String method = key.substring(0, key.indexOf(' '));
            String path = key.substring(key.indexOf(' ') + 1).replaceAll("\\{\\w+}", "1");
            MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}");
            var response = mockMvc.perform(request.header("Authorization", f.bearer(f.b))).andReturn().getResponse();
            String body = response.getContentAsString(StandardCharsets.UTF_8);
            if (response.getStatus() != 403 || !body.contains("ACCOUNT_SUSPENDED")) {
                notBlocked.add(key + "（" + response.getStatus() + "）");
            }
        }
        assertThat(notBlocked).as("利用停止中のユーザーが使えてしまう API").isEmpty();
    }

    /**
     * ログインしていなくても使える API（利用停止の確かめから除く。ログインの利用停止は ModerationVisibilityTest で確かめる）。
     * 申し立て（/api/contacts/appeal）は利用停止中の人のための API で、トークンではなく申し立て用の鍵を本文で受け取る
     * （ContactApiTest で確かめる）
     */
    private static final Set<String> PUBLIC_APIS = Set.of(
            "POST /api/auth/register", "POST /api/auth/login", "GET /api/health", "POST /api/contacts/appeal",
            "POST /api/auth/password-reset/request", "POST /api/auth/password-reset/confirm",
            "POST /api/auth/email-change/confirm");

    private void hide(Post post) {
        jdbcTemplate.update("UPDATE posts SET hidden_at = ?, hidden_reason = 'SPAM' WHERE id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()), post.getId());
    }

    private void suspend(User user) {
        jdbcTemplate.update("UPDATE users SET suspended_at = ?, suspended_until = NULL, suspension_reason = 'SPAM' "
                + "WHERE id = ?", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()), user.getId());
    }

    // ---- 3. ミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」） ----

    /**
     * A が C をミュートした前後で、登録した GET の API を A で呼ぶ。
     * HIDES（流れてくる一覧）は、前は C が出ていて後は出ない。SHOWS（探しに行く場所・自分で選んだもの・人の一覧）は、前も後も出ている。
     * 「前に出ている」ことも確かめるのは、出てくる場面を作れていないまま「出ない」を確かめても意味がないため
     */
    @Test
    void ミュートの後_流れてくる一覧にだけ相手の投稿が出ず_探しに行く場所には出る() throws Exception {
        prepareRelations();
        // お気に入りに C の投稿を入れておく（お気に入りは隠さないことを確かめるため）
        f.favorite(f.a, postOfC);

        List<String> problems = new ArrayList<>();
        Map<String, Boolean> before = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> registered : muteChecked()) {
            before.put(registered.getKey(), containsC(body(f.a, registered.getValue().pathForMute())));
        }

        f.as(f.a, MockMvcRequestBuilders.post("/api/users/" + f.c.getId() + "/mute")).andExpect(status().isNoContent());

        for (Map.Entry<String, Entry> registered : muteChecked()) {
            String key = registered.getKey();
            String path = render(registered.getValue().pathForMute(), f.a);
            boolean after = containsC(body(f.a, registered.getValue().pathForMute()));
            if (!before.get(key)) {
                problems.add(key + "（" + path + "）：ミュートの前に C が出ていない（確かめる場面を作れていない）");
            } else if (registered.getValue().mute() == Mute.HIDES && after) {
                problems.add(key + "（" + path + "）：ミュートした C の投稿が、流れてくる一覧に出ている（隠していない）");
            } else if (registered.getValue().mute() == Mute.SHOWS && !after) {
                problems.add(key + "（" + path + "）：ミュートした C が出なくなった（隠しすぎ。ブロックと同じになっている）");
            }
        }
        assertThat(problems).as("ミュートの扱いが登録と違う API").isEmpty();
    }

    /**
     * B の画面に A が出る場所（A が B をミュートしたときに、何かが変わるとしたらここ）。
     * {@code true} は「ミュートの前に A が出ている」ことも確かめる場所（A の名前・投稿が応答に入る API）
     */
    private static final Map<String, Boolean> PLACES_WHERE_B_SEES_A = new LinkedHashMap<>();

    static {
        PLACES_WHERE_B_SEES_A.put("/api/users/{b}/followers", true);           // B のフォロワー一覧に A がいる
        PLACES_WHERE_B_SEES_A.put("/api/users/{b}", false);                     // B のフォロワー数（A を含む）
        PLACES_WHERE_B_SEES_A.put("/api/notifications", true);                  // A からのいいね・フォロー・相談の申請の通知
        PLACES_WHERE_B_SEES_A.put("/api/notifications/summary", false);
        PLACES_WHERE_B_SEES_A.put("/api/users/{a}", true);                      // B が A のプロフィールを開く
        PLACES_WHERE_B_SEES_A.put("/api/users/{a}/posts", true);
        PLACES_WHERE_B_SEES_A.put("/api/users/{a}/followings", false);
        PLACES_WHERE_B_SEES_A.put("/api/users/{a}/followers", false);
        PLACES_WHERE_B_SEES_A.put("/api/users/{a}/following-posts?seed=7", false);
        PLACES_WHERE_B_SEES_A.put("/api/posts/{postA}", true);                  // A の投稿の詳細（いいね数・お気に入りを含む）
        PLACES_WHERE_B_SEES_A.put("/api/posts?tab=following", true);           // B は A をフォローしている
        PLACES_WHERE_B_SEES_A.put("/api/users/me/favorites", true);            // B は A の投稿をお気に入りにしている
        PLACES_WHERE_B_SEES_A.put("/api/search/users?q=blkuser_aaa", true);
        PLACES_WHERE_B_SEES_A.put("/api/conversations", true);                  // B と A の進行中の相談
        PLACES_WHERE_B_SEES_A.put("/api/conversations/{conversationAB}", true);
        PLACES_WHERE_B_SEES_A.put("/api/conversations/{conversationAB}/messages", false);
        PLACES_WHERE_B_SEES_A.put("/api/conversations/summary", false);
        PLACES_WHERE_B_SEES_A.put("/api/users/{a}/consultation-status", false);
        PLACES_WHERE_B_SEES_A.put("/api/users/consultation-statuses?ids={a}", false);
    }

    /**
     * A が B をミュートしても、B には分からない（docs/profile.md「ミュート（投稿の非表示）」の「相手に分かるか」）。
     * B の画面に A が出る場所と、登録したすべての GET の API を、ミュートの前後で B として呼び、応答がまったく同じであることを確かめる
     */
    @Test
    void ミュートされた人から見た応答は_ミュートの前後でまったく同じ() throws Exception {
        prepareRelations();
        long conversationAB = jdbcTemplate.queryForObject(
                "SELECT id FROM conversations WHERE requested_by_id = ?", Long.class, f.a.getId());
        // B の画面に A が出る場所に、A を出しておく（A が B の投稿にいいね・お気に入り・フォロー・相談の申請をしている。prepareRelations）
        Map<String, String> paths = new LinkedHashMap<>();
        PLACES_WHERE_B_SEES_A.keySet().forEach(path -> paths.put(path, withIds(path, conversationAB)));
        for (Map.Entry<String, Entry> registered : REGISTRY.entrySet()) {
            String path = registered.getValue().pathOrReason();
            if (registered.getValue().kind() == Kind.CHECKED && !path.contains("{forbiddenPost}")) {
                paths.putIfAbsent(registered.getKey(), path);
            }
        }

        // B が呼ぶこと自体で変わるもの（メッセージを開くと既読になり、相談の一覧の未読の数が0になる、など）があるため、
        // 一度すべて呼んで落ち着かせてから、ミュートの前の応答を取る（2026-11-26、これをしないと未読の数の違いで失敗した。ミュートとは関係ない）
        for (String path : paths.values()) {
            body(f.b, path);
        }

        Map<String, String> before = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> path : paths.entrySet()) {
            String body = normalized(body(f.b, path.getValue()));
            before.put(path.getKey(), body);
            if (Boolean.TRUE.equals(PLACES_WHERE_B_SEES_A.get(path.getKey())) && !containsA(body)) {
                problems.add(path.getKey() + "：ミュートの前に A が出ていない（確かめる場面を作れていない）");
            }
        }

        f.as(f.a, MockMvcRequestBuilders.post("/api/users/" + f.b.getId() + "/mute")).andExpect(status().isNoContent());

        for (Map.Entry<String, String> path : paths.entrySet()) {
            String after = normalized(body(f.b, path.getValue()));
            if (!after.equals(before.get(path.getKey()))) {
                problems.add(path.getKey() + "：A にミュートされた後、B への応答が変わった（相手に分かってしまう）\n  前: "
                        + before.get(path.getKey()) + "\n  後: " + after);
            }
        }
        assertThat(problems).as("ミュートされたことが相手に分かる API").isEmpty();
    }

    private String withIds(String path, long conversationAB) {
        return path.replace("{postA}", String.valueOf(postOfA.getId()))
                .replace("{conversationAB}", String.valueOf(conversationAB));
    }

    /** A のユーザー名・投稿の題名・投稿の写真のキーのどれかが含まれるか */
    private static boolean containsA(String body) {
        return body.contains("blkuser_aaa") || body.contains("BLKPOSTAAA")
                || body.contains(ImageTestSupport.key("BLKPOSTAAA.jpg"));
    }

    /**
     * 前後で比べるために、呼ぶたびに変わる値を除く。検索の API を呼ぶと検索履歴の日時が更新されるため、
     * 検索履歴の日時（searchedAt）だけを除く（ミュートとは関係なく、呼んだ時刻で変わる）
     */
    private static String normalized(String body) {
        return body.replaceAll("\"searchedAt\":\"[^\"]*\"", "\"searchedAt\":\"-\"");
    }

    /** ミュートの確かめをする登録（CHECKED で、NOT_RELATED でないもの） */
    private List<Map.Entry<String, Entry>> muteChecked() {
        return REGISTRY.entrySet().stream()
                .filter(e -> e.getValue().kind() == Kind.CHECKED && e.getValue().mute() != Mute.NOT_RELATED)
                .toList();
    }

    /** C のユーザー名・投稿の題名・投稿の写真のキーのどれかが含まれるか（一覧のカードは題名を持たないため、写真のキーも目印にする） */
    private static boolean containsC(String body) {
        return body.contains("blkuser_ccc") || body.contains("BLKPOSTCCC")
                || body.contains(ImageTestSupport.key("BLKPOSTCCC.jpg"));
    }

    /**
     * A・B・C の間に、一覧に出てくるつながりを作る（C は A・B の両方とフォローし合い、両方の投稿にいいねする。
     * A と B も互いにフォロー・いいね・お気に入り・相談をしている）
     */
    private void prepareRelations() throws Exception {
        postOfA = f.createPost(f.a, "BLKPOSTAAA");
        postOfB = f.createPost(f.b, "BLKPOSTBBB");
        postOfC = f.createPost(f.c, "BLKPOSTCCC");
        f.follow(f.a, f.c);
        f.follow(f.b, f.c);
        f.follow(f.c, f.a);
        f.follow(f.c, f.b);
        f.follow(f.a, f.b);
        f.follow(f.b, f.a);
        f.like(f.c, postOfA);
        f.like(f.c, postOfB);
        f.like(f.a, postOfB);
        f.like(f.b, postOfA);
        f.favorite(f.a, postOfB);
        f.favorite(f.b, postOfA);
        f.like(f.a, postOfC);
        f.like(f.b, postOfC);
        f.accept(f.b, f.requestConsultation(f.a, f.b));
        // 検索履歴にも残る（A・B が互いの名前で検索した）
        body(f.a, "/api/search/users?q=blkuser_bbb");
        body(f.b, "/api/search/users?q=blkuser_aaa");
        jdbcTemplate.update("DELETE FROM search_histories");
    }

    private List<String> findLeaks(String key, User viewer, String path, String... forbidden) throws Exception {
        // 見えてはいけない投稿の詳細（{forbiddenPost}）は、存在しない投稿と同じ 404 が正しい応答のため、404 の本文も確かめる
        String body = path.contains("{forbiddenPost}") ? bodyAllowingNotFound(viewer, path) : body(viewer, path);
        List<String> leaks = new ArrayList<>();
        for (String word : forbidden) {
            if (body.contains(word)) {
                leaks.add(key + "（" + viewer.getUsername() + " が呼んだ " + render(path, viewer) + "）に " + word + " が含まれる");
            }
            // 投稿の写真のキーも目印にする（BlockFixture.createPost は、題名から写真のキーを作る）。一覧のカード（{id, thumbnailUrl, …}）
            // のように、題名を持たない応答でも、漏れを見つけるため（2026-10-28、わざと漏らして、この目印だけでも見つかることを確かめた）
            String photo = ImageTestSupport.key(word + ".jpg");
            if (body.contains(photo)) {
                leaks.add(key + "（" + viewer.getUsername() + " が呼んだ " + render(path, viewer) + "）に " + word + " の写真が含まれる");
            }
        }
        return leaks;
    }

    private String body(User viewer, String path) throws Exception {
        return f.as(viewer, get(render(path, viewer)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bodyAllowingNotFound(User viewer, String path) throws Exception {
        MockHttpServletResponse response = f.as(viewer, get(render(path, viewer))).andReturn().getResponse();
        assertThat(response.getStatus()).as(render(path, viewer)).isIn(200, 404);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private String render(String path, User viewer) {
        String rendered = path.replace("{a}", String.valueOf(f.a.getId()))
                .replace("{b}", String.valueOf(f.b.getId()))
                .replace("{c}", String.valueOf(f.c.getId()))
                .replace("{postC}", postOfC == null ? "0" : String.valueOf(postOfC.getId()));
        if (rendered.contains("{forbiddenPost}")) {
            Post forbidden = forbiddenPostFor.get(viewer.getId());
            if (forbidden == null) {
                throw new IllegalStateException(viewer.getUsername() + " の見えてはいけない投稿（forbiddenPostFor）を決めていません: " + path);
            }
            rendered = rendered.replace("{forbiddenPost}", String.valueOf(forbidden.getId()));
        }
        return rendered;
    }
}
