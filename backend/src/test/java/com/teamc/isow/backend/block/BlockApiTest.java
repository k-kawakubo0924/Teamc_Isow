package com.teamc.isow.backend.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ブロック（POST/DELETE /api/users/{id}/block・GET /api/blocks。docs/profile.md「ブロック」）の確認。
 * ブロックした時点で2人の間のつながりを切ること、ブロックの効果が両方向であること、件数も見えない相手を数えないことを確かめる。
 * 一覧の API から相手が漏れていないことは BlockVisibilityTest で確かめる
 */
@SpringBootTest
@AutoConfigureMockMvc
class BlockApiTest {

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
        f.cleanUp();
    }

    // ---- ブロック・解除の操作 ----

    @Test
    void ブロックすると204で1行でき_2回目も204で行は増えない() throws Exception {
        f.block(a, b).andExpect(status().isNoContent());
        f.block(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM blocks WHERE blocker_id = ? AND blocked_id = ?", a.getId(), b.getId()))
                .isEqualTo(1);
        assertThat(f.count("SELECT count(*) FROM blocks WHERE blocker_id = ?", b.getId())).isZero();
    }

    @Test
    void 自分自身は400_存在しないユーザーは404_未ログインは401() throws Exception {
        f.block(a, a)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("自分自身はブロックできません。"));
        f.as(a, post("/api/users/999999999/block"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ユーザーが見つかりません。"));
        mockMvc.perform(post("/api/users/" + b.getId() + "/block")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/" + b.getId() + "/block")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/blocks")).andExpect(status().isUnauthorized());

        assertThat(f.count("SELECT count(*) FROM blocks")).isZero();
    }

    @Test
    void 解除すると行が消え_解除は何度でも204() throws Exception {
        f.block(a, b).andExpect(status().isNoContent());

        f.unblock(a, b).andExpect(status().isNoContent());
        f.unblock(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM blocks")).isZero();
        f.as(a, get("/api/users/" + b.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedByMe").value(false));
        f.as(b, get("/api/users/" + a.getId())).andExpect(status().isOk());
    }

    // ---- ブロックした時点で切るつながり ----

    @Test
    void ブロックすると両方向のフォローが消え_ほかの人とのフォローは残る() throws Exception {
        f.follow(a, b);
        f.follow(b, a);
        f.follow(a, c);
        f.follow(c, b);

        f.block(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM follows WHERE (follower_id = ? AND followee_id = ?) "
                + "OR (follower_id = ? AND followee_id = ?)", a.getId(), b.getId(), b.getId(), a.getId())).isZero();
        assertThat(f.count("SELECT count(*) FROM follows WHERE follower_id = ? AND followee_id = ? AND active = true",
                a.getId(), c.getId())).isEqualTo(1);
        assertThat(f.count("SELECT count(*) FROM follows WHERE follower_id = ? AND followee_id = ? AND active = true",
                c.getId(), b.getId())).isEqualTo(1);
    }

    @Test
    void 解除してもフォローは戻らない() throws Exception {
        f.follow(a, b);
        f.block(a, b).andExpect(status().isNoContent());

        f.unblock(a, b).andExpect(status().isNoContent());

        f.as(a, get("/api/users/" + b.getId())).andExpect(jsonPath("$.followingByMe").value(false));
    }

    @Test
    void 申請中の相談は拒否ではなく終了になり_終了した人はブロックした人() throws Exception {
        long conversationId = f.requestConsultation(b, a);
        long notificationsOfB = f.count("SELECT count(*) FROM notifications WHERE recipient_id = ?", b.getId());

        f.block(a, b).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM conversations WHERE id = ?", String.class,
                conversationId)).isEqualTo("ENDED");
        assertThat(f.count("SELECT count(*) FROM conversations WHERE id = ? AND ended_by_id = ?", conversationId, a.getId()))
                .isEqualTo(1);
        // 拒否の通知（「承認されませんでした」）は作らない
        assertThat(f.count("SELECT count(*) FROM notifications WHERE recipient_id = ?", b.getId()))
                .isEqualTo(notificationsOfB);
        assertThat(f.count("SELECT count(*) FROM notifications WHERE type = 'CONSULTATION_REJECTED'")).isZero();
    }

    @Test
    void 進行中の相談は終了になり_過去のメッセージは両方から読めるが新しく送れない() throws Exception {
        long conversationId = f.requestConsultation(a, b);
        f.accept(b, conversationId);

        f.block(a, b).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM conversations WHERE id = ?", String.class,
                conversationId)).isEqualTo("ENDED");
        f.as(a, get("/api/conversations/" + conversationId + "/messages")).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value("よろしくお願いします"));
        f.as(b, get("/api/conversations/" + conversationId + "/messages")).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value("よろしくお願いします"));
        int sendStatus = f.as(b, multipart("/api/conversations/" + conversationId + "/messages").param("body", "まだ送れる？"))
                .andReturn().getResponse().getStatus();
        assertThat(sendStatus).isGreaterThanOrEqualTo(400);
        assertThat(f.count("SELECT count(*) FROM messages WHERE conversation_id = ?", conversationId)).isEqualTo(1);
    }

    @Test
    void ブロックすると2人の間のいいね_お気に入り_通知が両方向で消え_ほかの人のものは残る() throws Exception {
        Post postOfA = f.createPost(a, "ブロック確認Aの投稿");
        Post postOfB = f.createPost(b, "ブロック確認Bの投稿");
        f.like(b, postOfA);
        f.favorite(b, postOfA);
        f.like(a, postOfB);
        f.favorite(a, postOfB);
        f.follow(b, a);
        f.like(c, postOfA);
        f.favorite(c, postOfB);
        assertThat(f.count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND actor_id = ?", a.getId(), b.getId()))
                .isEqualTo(2);

        f.block(a, b).andExpect(status().isNoContent());

        assertThat(f.count("SELECT count(*) FROM likes WHERE user_id IN (?, ?)", a.getId(), b.getId())).isZero();
        assertThat(f.count("SELECT count(*) FROM favorites WHERE user_id IN (?, ?)", a.getId(), b.getId())).isZero();
        assertThat(f.count("SELECT count(*) FROM notifications WHERE (recipient_id = ? AND actor_id = ?) "
                + "OR (recipient_id = ? AND actor_id = ?)", a.getId(), b.getId(), b.getId(), a.getId())).isZero();
        // C のいいね・お気に入りと、C から A への通知は残る
        assertThat(f.count("SELECT count(*) FROM likes WHERE user_id = ?", c.getId())).isEqualTo(1);
        assertThat(f.count("SELECT count(*) FROM favorites WHERE user_id = ?", c.getId())).isEqualTo(1);
        assertThat(f.count("SELECT count(*) FROM notifications WHERE actor_id = ?", c.getId())).isEqualTo(1);
    }

    // ---- 両方向の効果（1人ずつの API） ----

    @Test
    void ブロックした人から見た相手のプロフィールはブロック中と分かり_された人からは存在しないユーザーと同じ404() throws Exception {
        f.block(a, b).andExpect(status().isNoContent());

        f.as(a, get("/api/users/" + b.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("blkuser_bbb"))
                .andExpect(jsonPath("$.blockedByMe").value(true));
        f.as(b, get("/api/users/" + a.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ユーザーが見つかりません。"));
        // 関係のない人のプロフィールでは false
        f.as(a, get("/api/users/" + c.getId())).andExpect(jsonPath("$.blockedByMe").value(false));
    }

    @Test
    void 互いのプロフィールの一覧は両方向で404() throws Exception {
        f.block(a, b).andExpect(status().isNoContent());

        for (String suffix : new String[] {"/posts", "/following-posts?seed=1", "/followings", "/followers"}) {
            f.as(a, get("/api/users/" + b.getId() + suffix)).andExpect(status().isNotFound());
            f.as(b, get("/api/users/" + a.getId() + suffix)).andExpect(status().isNotFound());
        }
    }

    @Test
    void 互いの投稿の詳細_いいね_お気に入りは両方向で404() throws Exception {
        Post postOfA = f.createPost(a, "ブロック確認Aの投稿");
        Post postOfB = f.createPost(b, "ブロック確認Bの投稿");
        f.block(a, b).andExpect(status().isNoContent());

        for (Object[] pair : new Object[][] {{a, postOfB}, {b, postOfA}}) {
            User viewer = (User) pair[0];
            Post target = (Post) pair[1];
            f.as(viewer, get("/api/posts/" + target.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("投稿が見つかりません。"));
            f.as(viewer, post("/api/posts/" + target.getId() + "/like")).andExpect(status().isNotFound());
            f.as(viewer, post("/api/posts/" + target.getId() + "/favorite")).andExpect(status().isNotFound());
        }
        assertThat(f.count("SELECT count(*) FROM likes")).isZero();
        assertThat(f.count("SELECT count(*) FROM favorites")).isZero();
    }

    @Test
    void 互いの投稿の編集_削除は_存在しない投稿と同じ404で_投稿が残っていることも分からない() throws Exception {
        Post postOfA = f.createPost(a, "ブロック確認Aの投稿");
        Post postOfB = f.createPost(b, "ブロック確認Bの投稿");
        f.block(a, b).andExpect(status().isNoContent());

        for (Object[] pair : new Object[][] {{a, postOfB}, {b, postOfA}}) {
            User viewer = (User) pair[0];
            Post target = (Post) pair[1];
            f.as(viewer, put("/api/posts/" + target.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody(target)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("投稿が見つかりません。"));
            f.as(viewer, delete("/api/posts/" + target.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("投稿が見つかりません。"));
        }
        assertThat(f.count("SELECT count(*) FROM posts WHERE id IN (?, ?)", postOfA.getId(), postOfB.getId()))
                .isEqualTo(2);
        assertThat(f.count("SELECT count(*) FROM posts WHERE edited_at IS NOT NULL")).isZero();
    }

    @Test
    void ブロックの間柄でない他人の投稿の編集_削除は今までどおり403() throws Exception {
        Post postOfC = f.createPost(c, "ブロック確認Cの投稿");
        f.block(a, b).andExpect(status().isNoContent());

        f.as(a, put("/api/posts/" + postOfC.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody(postOfC)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("この投稿は編集できません。"));
        f.as(a, delete("/api/posts/" + postOfC.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("この投稿は削除できません。"));
    }

    /** 投稿の編集の本文（入力チェックは通る値。投稿の今のファッションの種類とタグを使う） */
    private String editBody(Post post) {
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT fashion_category_id FROM posts WHERE id = ?", Long.class, post.getId());
        String tag = jdbcTemplate.queryForObject("SELECT t.name FROM tags t JOIN post_tags pt ON pt.tag_id = t.id "
                + "WHERE pt.post_id = ?", String.class, post.getId());
        return "{\"title\": \"書き換え\", \"fashionCategoryId\": " + categoryId + ", \"tags\": [\"" + tag + "\"], "
                + "\"description\": \"書き換え\"}";
    }

    @Test
    void 互いにフォロー_相談の申込はできず_ブロックされたことは分からない404() throws Exception {
        f.block(a, b).andExpect(status().isNoContent());

        for (User[] pair : new User[][] {{a, b}, {b, a}}) {
            f.as(pair[0], post("/api/users/" + pair[1].getId() + "/follow"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("ユーザーが見つかりません。"));
            f.as(pair[0], get("/api/users/" + pair[1].getId() + "/consultation-status")).andExpect(status().isNotFound());
            f.as(pair[0], post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recipientId\": " + pair[1].getId() + "}"))
                    .andExpect(status().isNotFound());
        }
        assertThat(f.count("SELECT count(*) FROM follows")).isZero();
        assertThat(f.count("SELECT count(*) FROM conversations")).isZero();
        // まとめての確認では、存在しないユーザーと同じく結果に含めない
        f.as(b, get("/api/users/consultation-statuses").param("ids", a.getId() + "," + c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses[*].userId", contains(c.getId().intValue())));
    }

    @Test
    void お互いにブロックしていれば_片方が解除しても見えないまま() throws Exception {
        f.block(a, b).andExpect(status().isNoContent());
        f.block(b, a).andExpect(status().isNoContent());

        f.unblock(a, b).andExpect(status().isNoContent());

        f.as(a, get("/api/users/" + b.getId())).andExpect(status().isNotFound());
        f.as(b, get("/api/users/" + a.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedByMe").value(true));
    }

    // ---- 件数も見えない相手を数えない ----

    @Test
    void フォロー中_フォロワーの件数は_見ている人から見えない相手を数えない() throws Exception {
        f.follow(a, c);
        f.follow(b, c);
        f.follow(c, a);
        f.follow(c, b);
        f.block(a, b).andExpect(status().isNoContent());

        // C 本人からは2人ずつ
        f.as(c, get("/api/users/" + c.getId()))
                .andExpect(jsonPath("$.followerCount").value(2))
                .andExpect(jsonPath("$.followingCount").value(2));
        // A からは B を数えない（一覧と件数が合う）
        f.as(a, get("/api/users/" + c.getId()))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followingCount").value(1));
        f.as(a, get("/api/users/" + c.getId() + "/followers"))
                .andExpect(jsonPath("$.totalCount").value(1));
        // B からは A を数えない
        f.as(b, get("/api/users/" + c.getId()))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followingCount").value(1));
    }

    // ---- ブロック中の一覧 ----

    @Test
    void ブロック中の一覧は自分がブロックした人だけを新しい順に返す() throws Exception {
        f.block(a, b).andExpect(status().isNoContent());
        f.block(a, c).andExpect(status().isNoContent());
        f.block(c, b).andExpect(status().isNoContent());

        f.as(a, get("/api/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].userId", contains(c.getId().intValue(), b.getId().intValue())))
                .andExpect(jsonPath("$.items[0].username").value("blkuser_ccc"))
                .andExpect(jsonPath("$.items[0].blockedAt").isNotEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
        f.as(b, get("/api/blocks")).andExpect(jsonPath("$.items").isEmpty());
    }
}
