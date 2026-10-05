package com.teamc.isow.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.follow.Follow;
import com.teamc.isow.backend.follow.FollowRepository;
import com.teamc.isow.backend.master.BodyTypeRepository;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.master.PersonalColorRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostCardListResponse;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.reaction.PostFavorite;
import com.teamc.isow.backend.reaction.PostFavoriteRepository;
import com.teamc.isow.backend.reaction.PostLike;
import com.teamc.isow.backend.reaction.PostLikeRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
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
 * プロフィールと、プロフィールの投稿一覧・お気に入り一覧で、投稿ごとに SQL が発行されていないこと（N+1 問題）の確認。
 * 5件と20件で SQL の本数が同じであることを確かめる（件数に比例して増えるなら N+1 が入り込んでいる）。
 * 各投稿には写真3枚・タグ2つ・いいね2件・お気に入り1件を付け、関連の読み込みも発生させる。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProfileQueryCountTest {

    @Autowired
    private ProfileService profileService;

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
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        User m = new User(ProfileTestSupport.EMAIL_ME, "09000000030", "hash", "profile_me");
        // 骨格タイプ・パーソナルカラーを設定し、その読み込みも発生させる
        m.updateProfile("自分", Gender.MALE, 170, AgeGroup.LATE_20S,
                bodyTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0),
                personalColorRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0));
        me = userRepository.save(m);
        User o = new User(ProfileTestSupport.EMAIL_OTHER, "09000000031", "hash", "profile_other");
        o.updateProfile("相手", Gender.FEMALE, 158, AgeGroup.EARLY_20S,
                bodyTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(1),
                personalColorRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(1));
        other = userRepository.save(o);
        followRepository.save(new Follow(me, other));
        followRepository.save(new Follow(other, me));
    }

    @AfterEach
    void tearDown() {
        ProfileTestSupport.cleanUp(jdbcTemplate);
    }

    @Test
    void プロフィールと投稿一覧とお気に入り一覧は件数に関係なくSQLの本数が一定() {
        String subject = String.valueOf(me.getId());

        createPosts(5);
        long posts5 = countSql(() -> profileService.listPosts(subject, other.getId(), 0, 20), 5);
        long favorites5 = countSql(() -> profileService.listMyFavorites(subject, 0, 20), 5);

        createPosts(15);
        long posts20 = countSql(() -> profileService.listPosts(subject, other.getId(), 0, 20), 20);
        long favorites20 = countSql(() -> profileService.listMyFavorites(subject, 0, 20), 20);

        long mine = countSql(() -> profileService.getMine(subject), -1);
        long others = countSql(() -> profileService.get(subject, other.getId()), -1);

        System.out.printf(
                "### SQL の本数: 投稿一覧 %d → %d、お気に入り一覧 %d → %d（5件 → 20件）、自分のプロフィール %d、相手のプロフィール %d%n",
                posts5, posts20, favorites5, favorites20, mine, others);
        assertThat(posts20).isEqualTo(posts5);
        assertThat(favorites20).isEqualTo(favorites5);
    }

    /** 相手の投稿を作り、自分と相手がいいね、自分がお気に入りにする */
    private void createPosts(int count) {
        long existing = postRepository.count();
        for (int i = 0; i < count; i++) {
            Post post = ProfileTestSupport.savePost(
                    postRepository, fashionCategoryRepository, tagRepository, other, "q" + (existing + i));
            likeRepository.save(new PostLike(me, post));
            likeRepository.save(new PostLike(other, post));
            favoriteRepository.save(new PostFavorite(me, post));
        }
    }

    /** expectedPosts が 0 以上なら、一覧の件数も確かめる（データが取れていない状態で本数を比べないため） */
    private long countSql(Supplier<?> query, int expectedPosts) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        Object result = query.get();
        long count = statistics.getPrepareStatementCount();
        assertThat(result).isNotNull();
        if (expectedPosts >= 0) {
            assertThat(((PostCardListResponse) result).posts()).hasSize(expectedPosts);
        }
        return count;
    }
}
