package com.teamc.isow.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamc.isow.backend.dm.Conversation;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知（docs/notification.md）の保存と、DB の制約の確認。
 * 各テストはトランザクション内で行い、終了時にロールバックする（他のテストに影響させない）。
 */
@SpringBootTest
@Transactional
class NotificationEntityTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- 種類ごとの作成 ----

    @Test
    void いいねとフォローは_受け取る人_した人_関連する投稿が種類どおりに決まる() {
        User author = saveUser("a");
        User liker = saveUser("b");
        Post post = savePost(author);

        Notification liked = persist(Notification.liked(post, liker));
        Notification followed = persist(Notification.followed(author, liker));

        assertThat(liked.getType()).isEqualTo(NotificationType.LIKED);
        assertThat(liked.getRecipient().getId()).isEqualTo(author.getId());
        assertThat(liked.getActor().getId()).isEqualTo(liker.getId());
        assertThat(liked.getPost().getId()).isEqualTo(post.getId());
        assertThat(liked.getConversation()).isNull();

        assertThat(followed.getType()).isEqualTo(NotificationType.FOLLOWED);
        assertThat(followed.getRecipient().getId()).isEqualTo(author.getId());
        assertThat(followed.getActor().getId()).isEqualTo(liker.getId());
        assertThat(followed.getPost()).isNull();
        assertThat(followed.getConversation()).isNull();
    }

    @Test
    void 相談の通知は_届いたは申し込まれた人に_承認と拒否は申し込んだ人に届く() {
        User requester = saveUser("a");
        User recipient = saveUser("b");
        // 同じ2人の間で申請中・進行中の会話は1つまでのため、拒否した会話を先に作る
        Conversation rejected = persistConversation(new Conversation(requester, recipient));
        rejected.reject();
        Conversation approved = persistConversation(new Conversation(requester, recipient));
        approved.approve();

        Notification requested = persist(Notification.consultationRequested(approved));
        Notification approvedNotice = persist(Notification.consultationApproved(approved));
        Notification rejectedNotice = persist(Notification.consultationRejected(rejected));

        assertThat(requested.getType()).isEqualTo(NotificationType.CONSULTATION_REQUESTED);
        assertThat(requested.getRecipient().getId()).isEqualTo(recipient.getId());
        assertThat(requested.getActor().getId()).isEqualTo(requester.getId());
        assertThat(requested.getConversation().getId()).isEqualTo(approved.getId());

        assertThat(approvedNotice.getType()).isEqualTo(NotificationType.CONSULTATION_APPROVED);
        assertThat(approvedNotice.getRecipient().getId()).isEqualTo(requester.getId());
        assertThat(approvedNotice.getActor().getId()).isEqualTo(recipient.getId());

        assertThat(rejectedNotice.getType()).isEqualTo(NotificationType.CONSULTATION_REJECTED);
        assertThat(rejectedNotice.getRecipient().getId()).isEqualTo(requester.getId());
        assertThat(rejectedNotice.getConversation().getId()).isEqualTo(rejected.getId());
        assertThat(rejectedNotice.getPost()).isNull();
    }

    @Test
    void メッセージの通知は送った人の相手に届き_最初は未読で作成日時と通知日時が同じ() {
        User a = saveUser("a");
        User b = saveUser("b");
        Conversation conversation = persistConversation(new Conversation(a, b));

        Notification toA = persist(Notification.messageReceived(conversation, b));

        assertThat(toA.getType()).isEqualTo(NotificationType.MESSAGE_RECEIVED);
        assertThat(toA.getRecipient().getId()).isEqualTo(a.getId());
        assertThat(toA.getActor().getId()).isEqualTo(b.getId());
        assertThat(toA.isRead()).isFalse();
        assertThat(toA.getNotifiedAt()).isEqualTo(toA.getCreatedAt());
    }

    @Test
    void 種類は定数名の文字列で保存され_type列には値の一覧の検査制約が付かない() {
        User a = saveUser("a");
        User b = saveUser("b");
        Notification followed = persist(Notification.followed(a, b));

        String type = jdbcTemplate.queryForObject(
                "SELECT type FROM notifications WHERE id = ?", String.class, followed.getId());
        assertThat(type).isEqualTo("FOLLOWED");
        // まだない種類の値も保存できる（種類を増やしても、ddl-auto=update の DB に保存できなくならない）
        jdbcTemplate.update("INSERT INTO notifications (recipient_id, type, actor_id, created_at, notified_at) "
                + "VALUES (?, 'FUTURE_TYPE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", b.getId(), a.getId());
    }

    // ---- 自分の操作 ----

    @Test
    void 自分の操作では通知を作れない() {
        User a = saveUser("a");
        User b = saveUser("b");
        Post post = savePost(a);
        Conversation conversation = persistConversation(new Conversation(a, b));

        assertThatThrownBy(() -> Notification.liked(post, a)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.followed(a, a)).isInstanceOf(IllegalArgumentException.class);
        // 当事者でない人のメッセージの通知も作れない
        assertThatThrownBy(() -> Notification.messageReceived(conversation, saveUser("c")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 受け取る人と_した人が同じ行はDBでも保存できない() {
        User a = saveUser("a");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO notifications (recipient_id, type, actor_id, created_at, notified_at) "
                        + "VALUES (?, 'FOLLOWED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", a.getId(), a.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- メッセージの通知のまとめ ----

    @Test
    void 同じ会話で同じ人へのメッセージの通知は_2行目を保存できない() {
        User a = saveUser("a");
        User b = saveUser("b");
        Conversation conversation = persistConversation(new Conversation(a, b));
        persist(Notification.messageReceived(conversation, b));

        assertThatThrownBy(() -> persist(Notification.messageReceived(conversation, b)))
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void 同じ会話でも_相手側へのメッセージの通知は別の行になる() {
        User a = saveUser("a");
        User b = saveUser("b");
        Conversation conversation = persistConversation(new Conversation(a, b));

        Notification toA = persist(Notification.messageReceived(conversation, b));
        Notification toB = persist(Notification.messageReceived(conversation, a));

        assertThat(toA.getId()).isNotEqualTo(toB.getId());
        assertThat(notificationRepository.findMessageNotification(a.getId(), conversation.getId()))
                .get().extracting(Notification::getId).isEqualTo(toA.getId());
        assertThat(notificationRepository.findMessageNotification(b.getId(), conversation.getId()))
                .get().extracting(Notification::getId).isEqualTo(toB.getId());
    }

    @Test
    void 新しいメッセージが届いたら_通知日時を更新し_既読にした後でも未読に戻す() {
        User a = saveUser("a");
        User b = saveUser("b");
        Conversation conversation = persistConversation(new Conversation(a, b));
        Notification notification = persist(Notification.messageReceived(conversation, b));
        notification.markRead(LocalDateTime.now());
        entityManager.flush();
        assertThat(notification.isRead()).isTrue();
        // DB に保存された値と比べる（DB はマイクロ秒までしか持たないため）
        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM notifications WHERE id = ?", LocalDateTime.class, notification.getId());

        LocalDateTime arrivedAt = LocalDateTime.of(2030, 1, 1, 10, 0);
        notificationRepository.findMessageNotification(a.getId(), conversation.getId())
                .orElseThrow()
                .messageArrived(arrivedAt);
        entityManager.flush();
        entityManager.clear();

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloaded.isRead()).isFalse();
        assertThat(reloaded.getNotifiedAt()).isEqualTo(arrivedAt);
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isEqualTo(1);
    }

    @Test
    void メッセージ以外の通知はまとめない() {
        Notification followed = persist(Notification.followed(saveUser("a"), saveUser("b")));

        assertThatThrownBy(() -> followed.messageArrived(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- いいねの通知の重複 ----

    @Test
    void 同じ人が同じ投稿にいいねした通知は_2行目を保存できない() {
        User author = saveUser("a");
        User liker = saveUser("b");
        Post post = savePost(author);
        persist(Notification.liked(post, liker));

        assertThatThrownBy(() -> persist(Notification.liked(post, liker)))
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void 既存のいいねの通知は_同じ人と同じ投稿の組み合わせでだけ見つかる() {
        User author = saveUser("a");
        User liker = saveUser("b");
        User another = saveUser("c");
        Post post = savePost(author);
        Post otherPost = savePost(author);
        persist(Notification.liked(post, liker));

        assertThat(notificationRepository.existsLikeNotification(post.getId(), liker.getId())).isTrue();
        assertThat(notificationRepository.existsLikeNotification(post.getId(), another.getId())).isFalse();
        assertThat(notificationRepository.existsLikeNotification(otherPost.getId(), liker.getId())).isFalse();
    }

    @Test
    void 別の人や別の投稿へのいいね_フォローの通知は保存できる() {
        User author = saveUser("a");
        User liker = saveUser("b");
        User another = saveUser("c");
        Post post = savePost(author);
        Post otherPost = savePost(author);

        persist(Notification.liked(post, liker));
        persist(Notification.liked(post, another));
        persist(Notification.liked(otherPost, liker));
        // フォローは投稿を持たないため、いいねの重複の制約にはかからない
        persist(Notification.followed(author, liker));
        persist(Notification.followed(author, liker));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isEqualTo(5);
    }

    // ---- 既読・未読 ----

    @Test
    void 既読にした日時は最初のまま_未読に戻せる() {
        Notification notification = persist(Notification.followed(saveUser("a"), saveUser("b")));
        LocalDateTime first = LocalDateTime.now();

        notification.markRead(first);
        notification.markRead(first.plusMinutes(1));
        assertThat(notification.getReadAt()).isEqualTo(first);

        notification.markUnread();
        assertThat(notification.isRead()).isFalse();
    }

    // ---- 投稿の削除 ----

    @Test
    void 投稿を削除すると_いいねの通知もDBが自動で削除する() {
        User author = saveUser("a");
        User liker = saveUser("b");
        Post post = savePost(author);
        persist(Notification.liked(post, liker));
        Notification followed = persist(Notification.followed(author, liker));

        jdbcTemplate.update("DELETE FROM post_tags WHERE post_id = ?", post.getId());
        jdbcTemplate.update("DELETE FROM post_images WHERE post_id = ?", post.getId());
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", post.getId());

        assertThat(jdbcTemplate.queryForList("SELECT id FROM notifications", Long.class))
                .containsExactly(followed.getId());
    }

    // ---- 部品 ----

    private User saveUser(String name) {
        return userRepository.save(new User("notification-" + name + "@example.com",
                "0900001" + Math.abs(name.hashCode() % 10000), "hash", "notification_" + name));
    }

    private Post savePost(User author) {
        Post post = new Post(author, "題名", fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0),
                null, "説明", null, List.of("http://localhost/uploads/notification.jpg"),
                List.of(tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0)));
        entityManager.persist(post);
        entityManager.flush();
        return post;
    }

    /** 先に未反映の変更（会話の状態の変更など）を DB に書いてから保存する */
    private Conversation persistConversation(Conversation conversation) {
        entityManager.flush();
        entityManager.persist(conversation);
        entityManager.flush();
        return conversation;
    }

    private Notification persist(Notification notification) {
        entityManager.flush();
        entityManager.persist(notification);
        entityManager.flush();
        return notification;
    }
}
