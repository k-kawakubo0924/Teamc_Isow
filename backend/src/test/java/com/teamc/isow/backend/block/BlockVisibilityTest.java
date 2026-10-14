package com.teamc.isow.backend.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
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
class BlockVisibilityTest {

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

    /** 登録の内容。path は CHECKED のときに呼ぶ URL（{c}・{postC} などは下の render で置き換える） */
    private record Entry(Kind kind, String pathOrReason) {
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
        checked("GET /api/auth/me", "/api/auth/me");
        checked("GET /api/health", "/api/health");
        checked("GET /api/masters", "/api/masters");
        checked("GET /api/tags", "/api/tags?q=" + "古");

        // ---- お知らせ・通知（通知はブロックした時点で2人の間のものを消す） ----
        checked("GET /api/announcements", "/api/announcements");
        own("POST /api/announcements/{id}/read", "お知らせは全員に同じもので、他人が関わらない");
        checked("GET /api/notifications", "/api/notifications");
        checked("GET /api/notifications/summary", "/api/notifications/summary");
        own("POST /api/notifications/{id}/read", "自分の通知だけを扱う");
        own("POST /api/notifications/{id}/unread", "自分の通知だけを扱う");
        own("DELETE /api/notifications/{id}", "自分の通知だけを扱う");
        own("POST /api/notifications/read-all", "自分の通知だけを扱う");

        // ---- 投稿 ----
        own("POST /api/posts", "自分の投稿を作る");
        checked("GET /api/posts", "/api/posts?tab=latest");
        checked("GET /api/posts#following", "/api/posts?tab=following");
        checked("GET /api/posts#recommended", "/api/posts?tab=recommended");
        checked("GET /api/posts/me", "/api/posts/me");
        checked("GET /api/posts/{id}", "/api/posts/{postC}");
        // 自分の投稿だけを編集・削除できる。ブロックの間柄にある人の投稿は 404、見えるが他人の投稿は 403
        target("PUT /api/posts/{id}");
        target("DELETE /api/posts/{id}");
        target("POST /api/posts/{postId}/like");
        target("DELETE /api/posts/{postId}/like");
        target("POST /api/posts/{postId}/favorite");
        target("DELETE /api/posts/{postId}/favorite");

        // ---- プロフィール・フォロー ----
        checked("GET /api/users/me", "/api/users/me");
        own("PUT /api/users/me", "自分のプロフィールを変える");
        own("POST /api/users/me/profile-image", "自分のプロフィール画像を変える");
        checked("GET /api/users/me/favorites", "/api/users/me/favorites");
        checked("GET /api/users/{userId}", "/api/users/{c}");
        checked("GET /api/users/{userId}/posts", "/api/users/{c}/posts");
        checked("GET /api/users/{userId}/following-posts", "/api/users/{c}/following-posts?seed=7");
        checked("GET /api/users/{userId}/followings", "/api/users/{c}/followings");
        checked("GET /api/users/{userId}/followers", "/api/users/{c}/followers");
        target("POST /api/users/{userId}/follow");
        target("DELETE /api/users/{userId}/follow");

        // ---- ブロック ----
        target("POST /api/users/{userId}/block");
        own("DELETE /api/users/{userId}/block", "自分がしたブロックを解除するだけ");
        own("GET /api/blocks", "自分がブロックした人の一覧（ブロックした相手を出すための画面）");

        // ---- 通報（docs/admin.md「通報」） ----
        target("POST /api/reports");

        // ---- 検索 ----
        checked("GET /api/search/posts", "/api/search/posts?q=BLKPOST");
        checked("GET /api/search/users", "/api/search/users?q=blkuser");
        checked("GET /api/search/history", "/api/search/history");
        own("DELETE /api/search/history/{id}", "自分の検索履歴だけを扱う");
        own("DELETE /api/search/history", "自分の検索履歴だけを扱う");
        checked("GET /api/search/suggestions", "/api/search/suggestions");

        // ---- 相談（DM） ----
        target("POST /api/conversations");
        target("GET /api/users/{userId}/consultation-status");
        target("GET /api/users/consultation-statuses");
        checked("GET /api/conversations/summary", "/api/conversations/summary");
        exempt("GET /api/conversations", "ブロックした時点で相談は終了するが、過去の会話は両方から読めるまま残す（docs/dm.md「ブロック」）");
        exempt("GET /api/conversations/{conversationId}", "過去の会話は両方から読めるまま残す（docs/dm.md「ブロック」）");
        exempt("GET /api/conversations/{conversationId}/messages", "過去の会話は両方から読めるまま残す（docs/dm.md「ブロック」）");
        own("POST /api/conversations/{conversationId}/accept", "会話の当事者だけ。ブロックした時点で申請中の相談は終了しているため承認できない");
        own("POST /api/conversations/{conversationId}/reject", "会話の当事者だけ。ブロックした時点で申請中の相談は終了しているため拒否できない");
        own("POST /api/conversations/{conversationId}/end", "会話の当事者だけ。ブロックした時点で終了している");
        own("POST /api/conversations/{conversationId}/messages", "会話の当事者だけ。終了した会話には送れない（BlockApiTest）");
    }

    private static void checked(String key, String path) {
        REGISTRY.put(key, new Entry(Kind.CHECKED, path));
    }

    private static void target(String key) {
        REGISTRY.put(key, new Entry(Kind.TARGET_CHECKED_IN_BLOCK_API_TEST, "BlockApiTest"));
    }

    private static void own(String key, String reason) {
        REGISTRY.put(key, new Entry(Kind.OWN_DATA_ONLY, reason));
    }

    private static void exempt(String key, String reason) {
        REGISTRY.put(key, new Entry(Kind.EXEMPT, reason));
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
    private Post postOfC;

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
                .as("登録していない API があります。BlockVisibilityTest.REGISTRY に登録し、ブロックした相手が漏れないかを確かめてください")
                .isEmpty();
        assertThat(stale).as("もう存在しない API が登録に残っています").isEmpty();
        // 理由を書かずに確かめないものを作らない
        REGISTRY.forEach((key, entry) -> assertThat(entry.pathOrReason()).as(key).isNotBlank());
    }

    // ---- 2. 漏れの確認 ----

    @Test
    void ブロックの後_どのGETのAPIにも相手のユーザー名と投稿が出ない() throws Exception {
        prepareRelations();
        // 確かめる意味があるか：ブロックの前は、C の一覧・検索に A と B の両方が出ている
        assertThat(body(f.c, "/api/users/{c}/followers")).contains("blkuser_aaa", "blkuser_bbb");
        assertThat(body(f.a, "/api/search/posts?q=BLKPOST")).contains("BLKPOSTBBB");
        assertThat(body(f.b, "/api/search/users?q=blkuser")).contains("blkuser_aaa");

        f.block(f.a, f.b).andExpect(status().isNoContent());

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

    /**
     * A・B・C の間に、一覧に出てくるつながりを作る（C は A・B の両方とフォローし合い、両方の投稿にいいねする。
     * A と B も互いにフォロー・いいね・お気に入り・相談をしている）
     */
    private void prepareRelations() throws Exception {
        Post postOfA = f.createPost(f.a, "BLKPOSTAAA");
        Post postOfB = f.createPost(f.b, "BLKPOSTBBB");
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
        String body = body(viewer, path);
        List<String> leaks = new ArrayList<>();
        for (String word : forbidden) {
            if (body.contains(word)) {
                leaks.add(key + "（" + viewer.getUsername() + " が呼んだ " + render(path) + "）に " + word + " が含まれる");
            }
        }
        return leaks;
    }

    private String body(User viewer, String path) throws Exception {
        return f.as(viewer, get(render(path)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String render(String path) {
        return path.replace("{c}", String.valueOf(f.c.getId()))
                .replace("{postC}", postOfC == null ? "0" : String.valueOf(postOfC.getId()));
    }
}
