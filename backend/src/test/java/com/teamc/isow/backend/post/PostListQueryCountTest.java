package com.teamc.isow.backend.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.reaction.PostFavorite;
import com.teamc.isow.backend.reaction.PostFavoriteRepository;
import com.teamc.isow.backend.reaction.PostLike;
import com.teamc.isow.backend.reaction.PostLikeRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 投稿の一覧で、投稿ごとに SQL が発行されていないこと（N+1 問題）の確認。
 * 5件と20件で SQL の本数が同じであることを確かめる（件数に比例して増えるなら N+1 が入り込んでいる）。
 * 各投稿には写真3枚・タグ2つ・いいね2件・お気に入り1件を付け、関連の読み込みも発生させる。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PostListQueryCountTest {

    private static final String OTHER_EMAIL = "query-count-other@example.com";

    @Autowired
    private TimelineService timelineService;

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PostLikeRepository likeRepository;

    @Autowired
    private PostFavoriteRepository favoriteRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(PostApiTestSupport.EMAIL, "09000000000", "hash", "query_count_me"));
        other = userRepository.save(new User(OTHER_EMAIL, "09000000001", "hash", "query_count_other"));
    }

    @AfterEach
    void tearDown() {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        PostApiTestSupport.cleanUp(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", OTHER_EMAIL);
    }

    @Test
    void ホームの一覧と自分の投稿一覧は件数に関係なくSQLの本数が一定() {
        String subject = String.valueOf(me.getId());

        createPosts(5);
        long recommended5 = countSql(() -> timelineService.list(subject, TimelineTab.RECOMMENDED, 0, 20));
        long latest5 = countSql(() -> timelineService.list(subject, TimelineTab.LATEST, 0, 20));
        long mine5 = countSql(() -> postService.listMine(subject, 0, 20));

        createPosts(15);
        long recommended20 = countSql(() -> timelineService.list(subject, TimelineTab.RECOMMENDED, 0, 20));
        long latest20 = countSql(() -> timelineService.list(subject, TimelineTab.LATEST, 0, 20));
        long mine20 = countSql(() -> postService.listMine(subject, 0, 20));

        System.out.printf("### SQL の本数（5件 → 20件）: おすすめ %d → %d、新着 %d → %d、自分の投稿一覧 %d → %d%n",
                recommended5, recommended20, latest5, latest20, mine5, mine20);
        assertThat(recommended20).isEqualTo(recommended5);
        assertThat(latest20).isEqualTo(latest5);
        assertThat(mine20).isEqualTo(mine5);
    }

    @Test
    void 投稿の詳細は写真の枚数とタグの数に関係なくSQLの本数が一定() {
        String subject = String.valueOf(me.getId());
        List<FashionCategory> categories = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        List<Tag> tags = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc();

        // 写真1枚・タグ1つの投稿と、写真10枚・タグ10個の投稿（どちらも他人の投稿に、いいね・お気に入りあり）
        Post small = postRepository.save(new Post(other, "少ない", categories.get(0), null, "説明", null,
                List.of("http://localhost/uploads/s-1.jpg"), List.of(tags.get(0))));
        List<String> tenImages = new java.util.ArrayList<>();
        for (int i = 1; i <= Post.MAX_IMAGES; i++) {
            tenImages.add("http://localhost/uploads/l-" + i + ".jpg");
        }
        Post large = postRepository.save(new Post(other, "多い", categories.get(0), "着用アイテム", "説明",
                "https://example.com", tenImages, tags.subList(0, 10)));
        for (Post post : List.of(small, large)) {
            likeRepository.save(new PostLike(me, post));
            favoriteRepository.save(new PostFavorite(me, post));
        }

        long smallCount = countSql(() -> postService.get(subject, small.getId()));
        long largeCount = countSql(() -> postService.get(subject, large.getId()));

        System.out.printf("### SQL の本数（投稿の詳細。写真1枚・タグ1つ → 写真10枚・タグ10個）: %d → %d%n",
                smallCount, largeCount);
        assertThat(largeCount).isEqualTo(smallCount);
        // ログイン確認1、投稿・投稿者・ファッションの種類1、写真1、タグ1、いいね・お気に入り3
        assertThat(largeCount).isEqualTo(7);
        PostResponse detail = postService.get(subject, large.getId());
        assertThat(detail.imageUrls()).hasSize(Post.MAX_IMAGES);
        assertThat(detail.tags()).hasSize(10);
    }

    /** 自分と他人の投稿を交互に作る（ホームの一覧では投稿者が複数いる状態にする） */
    private void createPosts(int count) {
        List<FashionCategory> categories = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        List<Tag> tags = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc();
        long existing = postRepository.count();
        for (int i = 0; i < count; i++) {
            long n = existing + i;
            Post post = postRepository.save(new Post(
                    // 自分の投稿一覧の件数も増えるよう、半分は自分の投稿にする
                    n % 2 == 0 ? me : other,
                    "投稿" + n,
                    categories.get((int) (n % categories.size())),
                    null, "説明", null,
                    List.of("http://localhost/uploads/" + n + "-1.jpg", "http://localhost/uploads/" + n + "-2.jpg",
                            "http://localhost/uploads/" + n + "-3.jpg"),
                    List.of(tags.get((int) (n % tags.size())), tags.get((int) ((n + 1) % tags.size())))));
            likeRepository.save(new PostLike(me, post));
            likeRepository.save(new PostLike(other, post));
            favoriteRepository.save(new PostFavorite(me, post));
        }
    }

    private long countSql(Supplier<?> query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        Object result = query.get();
        assertThat(result).isNotNull();
        return statistics.getPrepareStatementCount();
    }
}
