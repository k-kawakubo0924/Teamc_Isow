package com.teamc.isow.backend.mute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.block.BlockFixture;
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

/**
 * ミュート（投稿の非表示。POST/DELETE /api/users/{id}/mute・GET /api/mutes。docs/profile.md「ミュート（投稿の非表示）」）の確認。
 *
 * <p>登場人物は BlockFixture と同じ：A がミュートする人、B がミュートされる人、C は第三者。
 *
 * <p><b>ブロックとの違いの中心は「ミュートは何も消さない」こと</b>。ブロックはフォロー・いいね・お気に入り・通知・進行中の相談を消す
 * （BlockService）。間違ってブロックの処理を呼んだときに、どれが消えたのか一目で分かるよう、残るものを1つずつ別のテストで確かめる。
 * どの一覧で隠れるか（流れてくる一覧だけ）は、VisibilityInventoryTest のミュートの欄で確かめる
 */
@SpringBootTest
@AutoConfigureMockMvc
class MuteApiTest {

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

    @BeforeEach
    void setUp() {
        f = new BlockFixture(mockMvc, jwtEncoder, userRepository, postRepository, fashionCategoryRepository,
                tagRepository, jdbcTemplate);
        f.createUsers();
        a = f.a;
        b = f.b;
        c = f.c;
    }

    @AfterEach
    void tearDown() {
        // ミュートの行はユーザーの削除で一緒に消える（ON DELETE CASCADE）
        f.cleanUp();
    }

    private ResultActions mute(User from, User to) throws Exception {
        return f.as(from, post("/api/users/" + to.getId() + "/mute"));
    }

    private ResultActions unmute(User from, User to) throws Exception {
        return f.as(from, delete("/api/users/" + to.getId() + "/mute"));
    }

    private long mutes(User muter, User muted) {
        return f.count("SELECT count(*) FROM user_mutes WHERE muter_id = ? AND muted_id = ?", muter.getId(), muted.getId());
    }

    private void suspend(User user) {
        jdbcTemplate.update("UPDATE users SET suspended_at = CURRENT_TIMESTAMP, suspended_until = NULL WHERE id = ?",
                user.getId());
    }

    // ---- ミュート・解除の操作 ----

    @Test
    void ミュートすると204で1行でき_2回目も204で行は増えない() throws Exception {
        mute(a, b).andExpect(status().isNoContent());
        mute(a, b).andExpect(status().isNoContent());

        assertThat(mutes(a, b)).isEqualTo(1);
        // 片方向：B から A へのミュートはできない
        assertThat(mutes(b, a)).isZero();
    }

    @Test
    void 自分自身は400_存在しないユーザーは404_未ログインは401() throws Exception {
        mute(a, a)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("自分の投稿は非表示にできません。"));
        f.as(a, post("/api/users/999999999/mute"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ユーザーが見つかりません。"));
        f.as(a, delete("/api/users/999999999/mute")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users/" + b.getId() + "/mute")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/" + b.getId() + "/mute")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mutes")).andExpect(status().isUnauthorized());

        assertThat(f.count("SELECT count(*) FROM user_mutes")).isZero();
    }

    @Test
    void ブロックの間柄にある相手は_どちらの向きでも存在しないユーザーと同じ404() throws Exception {
        f.block(b, a).andExpect(status().isNoContent());

        mute(a, b).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("ユーザーが見つかりません。"));
        mute(b, a).andExpect(status().isNotFound());

        assertThat(f.count("SELECT count(*) FROM user_mutes")).isZero();
    }

    @Test
    void 利用停止中の相手は存在しないユーザーと同じ404() throws Exception {
        suspend(b);

        mute(a, b).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("ユーザーが見つかりません。"));

        assertThat(mutes(a, b)).isZero();
    }

    @Test
    void 解除すると行が消え_解除は何度でも204_相手のミュートは残る() throws Exception {
        mute(a, b).andExpect(status().isNoContent());
        mute(b, a).andExpect(status().isNoContent());

        unmute(a, b).andExpect(status().isNoContent());
        unmute(a, b).andExpect(status().isNoContent());

        assertThat(mutes(a, b)).isZero();
        assertThat(mutes(b, a)).as("解除できるのは自分がしたミュートだけ").isEqualTo(1);
    }

    @Test
    void ミュートしている相手をブロックしてもミュートの行は残り_ブロックを解除するとミュートした状態に戻る() throws Exception {
        mute(a, b).andExpect(status().isNoContent());

        f.block(a, b).andExpect(status().isNoContent());
        assertThat(mutes(a, b)).as("ブロックしてもミュートの行は消さない").isEqualTo(1);

        f.unblock(a, b).andExpect(status().isNoContent());
        assertThat(mutes(a, b)).isEqualTo(1);
    }

    @Test
    void プロフィールのmutedByMeは_ミュートした本人から見たときだけtrueで_解除するとfalseに戻る() throws Exception {
        f.as(a, get("/api/users/" + b.getId())).andExpect(jsonPath("$.mutedByMe").value(false));

        mute(a, b).andExpect(status().isNoContent());

        f.as(a, get("/api/users/" + b.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.mutedByMe").value(true));
        // ミュートされた人・第三者・自分のプロフィールでは false（相手に分からない）
        f.as(b, get("/api/users/" + a.getId())).andExpect(jsonPath("$.mutedByMe").value(false));
        f.as(c, get("/api/users/" + b.getId())).andExpect(jsonPath("$.mutedByMe").value(false));
        f.as(a, get("/api/users/" + a.getId())).andExpect(jsonPath("$.mutedByMe").value(false));

        unmute(a, b).andExpect(status().isNoContent());
        f.as(a, get("/api/users/" + b.getId())).andExpect(jsonPath("$.mutedByMe").value(false));
    }

    @Test
    void 投稿の詳細のauthorMutedByMeは_投稿者をミュートした本人から見たときだけtrue() throws Exception {
        Post postOfA = f.createPost(a, "ミュート確認Aの投稿");
        Post postOfB = f.createPost(b, "ミュート確認Bの投稿");
        f.as(a, get("/api/posts/" + postOfB.getId())).andExpect(jsonPath("$.authorMutedByMe").value(false));

        mute(a, b).andExpect(status().isNoContent());

        // ミュートしても投稿の詳細は見える（自分から探しに行った場所）。画面のメニューを「この人の投稿を表示する」にするための値
        f.as(a, get("/api/posts/" + postOfB.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorMutedByMe").value(true));
        // ミュートされた人・第三者・自分の投稿では false（相手に分からない）
        f.as(b, get("/api/posts/" + postOfA.getId())).andExpect(jsonPath("$.authorMutedByMe").value(false));
        f.as(c, get("/api/posts/" + postOfB.getId())).andExpect(jsonPath("$.authorMutedByMe").value(false));
        f.as(a, get("/api/posts/" + postOfA.getId())).andExpect(jsonPath("$.authorMutedByMe").value(false));
    }

    // ---- ミュートは何も消さない（ブロックとの違いの中心。1つずつ確かめる） ----

    @Test
    void ミュートしてもブロックの行はできない() throws Exception {
        mute(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM blocks"))
                .as("ミュートがブロックの行を作った（ブロックの処理を呼んでいる）")
                .isZero();
    }

    @Test
    void ミュートしても_ミュートした人からされた人へのフォローは残る() throws Exception {
        f.follow(a, b);

        mute(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM follows WHERE follower_id = ? AND followee_id = ? AND active = true",
                a.getId(), b.getId()))
                .as("ミュートで A→B のフォローが消えた")
                .isEqualTo(1);
        f.as(a, get("/api/users/" + b.getId())).andExpect(jsonPath("$.followingByMe").value(true));
    }

    @Test
    void ミュートしても_ミュートされた人からした人へのフォローは残る() throws Exception {
        f.follow(b, a);

        mute(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM follows WHERE follower_id = ? AND followee_id = ? AND active = true",
                b.getId(), a.getId()))
                .as("ミュートで B→A のフォローが消えた")
                .isEqualTo(1);
        f.as(b, get("/api/users/" + a.getId())).andExpect(jsonPath("$.followingByMe").value(true));
    }

    @Test
    void ミュートしても_2人の間のいいねは両方向とも残る() throws Exception {
        Post postOfA = f.createPost(a, "ミュート確認Aの投稿");
        Post postOfB = f.createPost(b, "ミュート確認Bの投稿");
        f.like(a, postOfB);
        f.like(b, postOfA);

        mute(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM likes WHERE user_id = ? AND post_id = ?", a.getId(), postOfB.getId()))
                .as("ミュートで A が B の投稿に付けたいいねが消えた")
                .isEqualTo(1);
        assertThat(f.count("SELECT count(*) FROM likes WHERE user_id = ? AND post_id = ?", b.getId(), postOfA.getId()))
                .as("ミュートで B が A の投稿に付けたいいねが消えた")
                .isEqualTo(1);
    }

    @Test
    void ミュートしても_2人の間のお気に入りは両方向とも残る() throws Exception {
        Post postOfA = f.createPost(a, "ミュート確認Aの投稿");
        Post postOfB = f.createPost(b, "ミュート確認Bの投稿");
        f.favorite(a, postOfB);
        f.favorite(b, postOfA);

        mute(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM favorites WHERE user_id = ? AND post_id = ?", a.getId(), postOfB.getId()))
                .as("ミュートで A が B の投稿に付けたお気に入りが消えた")
                .isEqualTo(1);
        assertThat(f.count("SELECT count(*) FROM favorites WHERE user_id = ? AND post_id = ?", b.getId(), postOfA.getId()))
                .as("ミュートで B が A の投稿に付けたお気に入りが消えた")
                .isEqualTo(1);
    }

    @Test
    void ミュートしても_2人の間の通知は両方向とも残る() throws Exception {
        Post postOfA = f.createPost(a, "ミュート確認Aの投稿");
        Post postOfB = f.createPost(b, "ミュート確認Bの投稿");
        f.like(b, postOfA);
        f.like(a, postOfB);
        long toA = f.count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND actor_id = ?", a.getId(), b.getId());
        long toB = f.count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND actor_id = ?", b.getId(), a.getId());
        assertThat(toA).as("前提：B から A への通知がある").isPositive();
        assertThat(toB).as("前提：A から B への通知がある").isPositive();

        mute(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND actor_id = ?", a.getId(), b.getId()))
                .as("ミュートで B から A への通知が消えた")
                .isEqualTo(toA);
        assertThat(f.count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND actor_id = ?", b.getId(), a.getId()))
                .as("ミュートで A から B への通知が消えた")
                .isEqualTo(toB);
    }

    @Test
    void ミュートしても_進行中の相談は終了せず_メッセージを送り合える() throws Exception {
        long conversationId = f.requestConsultation(a, b);
        f.accept(b, conversationId);

        mute(a, b).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM conversations WHERE id = ?", String.class, conversationId))
                .as("ミュートで進行中の相談が終了した")
                .isEqualTo("ACTIVE");
        f.as(a, multipart("/api/conversations/" + conversationId + "/messages").param("body", "ミュート後もAから送れる"))
                .andExpect(status().is2xxSuccessful());
        f.as(b, multipart("/api/conversations/" + conversationId + "/messages").param("body", "ミュート後もBから送れる"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void ミュートしても_申請中の相談は終了しない() throws Exception {
        long conversationId = f.requestConsultation(b, a);

        mute(a, b).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM conversations WHERE id = ?", String.class, conversationId))
                .as("ミュートで申請中の相談が終了した")
                .isEqualTo("REQUESTED");
    }

    // ---- 相手に分からない（応答が変わらないことは VisibilityInventoryTest で確かめる） ----

    @Test
    void ミュートされた人からした人への操作は_ミュートで断られない() throws Exception {
        Post postOfA = f.createPost(a, "ミュート確認Aの投稿");
        mute(a, b).andExpect(status().isNoContent());

        // 断られる・応答が変わると、その違いからミュートされたことが分かってしまう
        f.follow(b, a);
        f.like(b, postOfA);
        f.favorite(b, postOfA);
        long conversationId = f.requestConsultation(b, a);
        f.accept(a, conversationId);
        f.as(b, multipart("/api/conversations/" + conversationId + "/messages").param("body", "ミュートされていても送れる"))
                .andExpect(status().is2xxSuccessful());

        // A への通知も、ミュートと関係なく届く（通知はミュートで変えない。案A）
        assertThat(f.count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND actor_id = ? AND type = 'LIKED'",
                a.getId(), b.getId())).isEqualTo(1);
        assertThat(f.count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND actor_id = ? AND type = 'FOLLOWED'",
                a.getId(), b.getId())).isEqualTo(1);
    }

    // ---- ミュートしている人の一覧 ----

    @Test
    void 一覧はミュートした新しい順で_自分がしたミュートだけを出す() throws Exception {
        mute(a, b).andExpect(status().isNoContent());
        mute(a, c).andExpect(status().isNoContent());
        mute(b, c).andExpect(status().isNoContent());
        // 同じ時刻になっても並び順を確かめられるよう、B を先にミュートした日時にずらす
        jdbcTemplate.update("UPDATE user_mutes SET muted_at = muted_at - INTERVAL '1' HOUR WHERE muter_id = ? AND muted_id = ?",
                a.getId(), b.getId());

        f.as(a, get("/api/mutes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].userId").value(c.getId()))
                .andExpect(jsonPath("$.items[0].username").value("blkuser_ccc"))
                .andExpect(jsonPath("$.items[0].mutedAt").exists())
                .andExpect(jsonPath("$.items[1].userId").value(b.getId()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 一覧はページで区切る() throws Exception {
        mute(a, b).andExpect(status().isNoContent());
        mute(a, c).andExpect(status().isNoContent());

        f.as(a, get("/api/mutes?page=0&size=1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
        f.as(a, get("/api/mutes?page=1&size=1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 一覧に_ブロックの間柄になった相手と利用停止中の相手は出さない() throws Exception {
        mute(a, b).andExpect(status().isNoContent());
        mute(a, c).andExpect(status().isNoContent());

        f.block(b, a).andExpect(status().isNoContent());
        suspend(c);

        f.as(a, get("/api/mutes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        // 行は残す（ブロックを解除する・利用停止が解けると、ミュートした状態に戻る）
        assertThat(mutes(a, b)).isEqualTo(1);
        assertThat(mutes(a, c)).isEqualTo(1);
    }
}
