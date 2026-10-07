package com.teamc.isow.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.dm.Conversation;
import com.teamc.isow.backend.dm.ConversationRepository;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
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
 * 通知一覧で、1件ごとに SQL が発行されていないこと（N+1 問題）の確認。
 * 5件と20件で SQL の本数が同じであることを確かめる。通知はいいね（投稿・写真あり）・フォロー・メッセージを混ぜ、
 * 関連するユーザーは通知ごとに別の人にする（関連の読み込みも発生させる）。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class NotificationQueryCountTest {

    private static final String ME_EMAIL = "notif-count-me@example.com";

    @Autowired
    private NotificationQueryService notificationQueryService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(ME_EMAIL, "09000000100", "hash", "notif_count_me"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users WHERE email = ? OR email LIKE 'notif-count-u%@example.com'", ME_EMAIL);
    }

    @Test
    void 通知一覧と未読件数と操作は件数に関係なくSQLの本数が一定() {
        String subject = String.valueOf(me.getId());

        createNotifications(0, 5);
        long list5 = countSql(() -> notificationQueryService.list(subject, 0, 20));
        long summary5 = countSql(() -> notificationQueryService.summary(subject));

        createNotifications(5, 20);
        long list20 = countSql(() -> notificationQueryService.list(subject, 0, 20));
        long summary20 = countSql(() -> notificationQueryService.summary(subject));

        // いいねの通知がないページ（サムネイルを読まない）
        jdbcTemplate.update("DELETE FROM notifications WHERE type = 'LIKED'");
        long listWithoutLikes = countSql(() -> notificationQueryService.list(subject, 0, 20));

        Long id = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM notifications WHERE recipient_id = ?", Long.class, me.getId());
        long read = countSql(() -> run(() -> notificationQueryService.markRead(subject, id)));
        long unread = countSql(() -> run(() -> notificationQueryService.markUnread(subject, id)));
        long readAll = countSql(() -> run(() -> notificationQueryService.markAllRead(subject)));
        long delete = countSql(() -> run(() -> notificationQueryService.delete(subject, id)));

        System.out.printf("### SQL の本数（5件 → 20件）: 一覧 %d → %d（いいねなし %d）、未読件数 %d → %d、"
                        + "既読 %d、未読に戻す %d、すべて既読 %d、削除 %d%n",
                list5, list20, listWithoutLikes, summary5, summary20, read, unread, readAll, delete);
        assertThat(list20).isEqualTo(list5);
        assertThat(summary20).isEqualTo(summary5);
    }

    /** from 番目から to 番目の手前まで、いいね・フォロー・メッセージの通知を順に作る（相手は毎回別の人） */
    private void createNotifications(int from, int to) {
        for (int i = from; i < to; i++) {
            User actor = userRepository.save(new User("notif-count-u" + i + "@example.com",
                    String.format("0907100%04d", i), "hash", "notif_count_u" + i));
            switch (i % 3) {
                case 0 -> notificationRepository.save(Notification.liked(savePost(), actor));
                case 1 -> notificationRepository.save(Notification.followed(me, actor));
                default -> notificationRepository.save(Notification.messageReceived(
                        conversationRepository.save(new Conversation(actor, me)), actor));
            }
        }
    }

    private Post savePost() {
        return postRepository.save(new Post(me, "題名",
                fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0), null, "説明", null,
                List.of("http://localhost/uploads/1.jpg", "http://localhost/uploads/2.jpg"),
                List.of(tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0))));
    }

    private static Object run(Runnable action) {
        action.run();
        return true;
    }

    private long countSql(Supplier<?> query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        Object result = query.get();
        assertThat(result).isNotNull();
        return statistics.getPrepareStatementCount();
    }
}
