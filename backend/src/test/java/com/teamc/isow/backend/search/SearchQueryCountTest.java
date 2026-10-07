package com.teamc.isow.backend.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
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
 * 投稿の検索で、投稿ごとに SQL が発行されていないこと（N+1 問題）の確認。
 * 5件と20件で SQL の本数が同じであることを確かめる（件数に比例して増えるなら N+1 が入り込んでいる）。
 * 各投稿には写真3枚・タグ2つ・いいね2件・お気に入り1件を付け、関連の読み込みも発生させる。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SearchQueryCountTest {

    private static final String ME_EMAIL = "search-count-me@example.com";
    private static final String OTHER_EMAIL = "search-count-other@example.com";

    @Autowired
    private SearchService searchService;

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
    private FashionCategory category;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(ME_EMAIL, "09000000060", "hash", "search_count_me"));
        other = userRepository.save(new User(OTHER_EMAIL, "09000000061", "hash", "search_count_other"));
        jdbcTemplate.update("UPDATE users SET age_group = ? WHERE id = ?", AgeGroup.EARLY_20S.name(), other.getId());
        category = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM search_histories");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", ME_EMAIL, OTHER_EMAIL);
    }

    @Test
    void 検索は件数に関係なくSQLの本数が一定() {
        String subject = String.valueOf(me.getId());
        String categoryId = String.valueOf(category.getId());
        // 1ページ目の件数がページの大きさ未満だと件数を数える SQL が省かれるため、size は件数より小さくする
        Supplier<?> noCondition = () -> searchService.searchPosts(subject, null, null, null, 0, 4);
        Supplier<?> allConditions =
                () -> searchService.searchPosts(subject, "コート", categoryId, "EARLY_20S", 0, 4);

        createPosts(5);
        long noCondition5 = countSql(noCondition);
        long allConditions5 = countSql(allConditions);

        createPosts(15);
        // 2回目の同じキーワードは履歴の日時の更新になり SQL が変わるため、1回目と同じく新しく記録する状態にそろえる
        jdbcTemplate.update("DELETE FROM search_histories");
        Supplier<?> noConditionPage20 = () -> searchService.searchPosts(subject, null, null, null, 0, 19);
        Supplier<?> allConditionsPage20 =
                () -> searchService.searchPosts(subject, "コート", categoryId, "EARLY_20S", 0, 19);
        long noCondition20 = countSql(noConditionPage20);
        long allConditions20 = countSql(allConditionsPage20);

        System.out.printf("### SQL の本数（4件 → 19件のページ）: 条件なし %d → %d、すべての条件 %d → %d%n",
                noCondition5, noCondition20, allConditions5, allConditions20);
        assertThat(noCondition20).isEqualTo(noCondition5);
        assertThat(allConditions20).isEqualTo(allConditions5);
    }

    /** 条件に一致する投稿を作る（投稿者は年代を設定した other） */
    private void createPosts(int count) {
        List<Tag> tags = tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc();
        long existing = postRepository.count();
        for (int i = 0; i < count; i++) {
            long n = existing + i;
            Post post = postRepository.save(new Post(other, "コート" + n, category, null, "説明", null,
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
