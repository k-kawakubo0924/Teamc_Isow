package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会話・メッセージ（docs/dm.md）の保存と、DB の制約の確認。
 * 各テストはトランザクション内で行い、終了時にロールバックする（他のテストに影響させない）。
 */
@SpringBootTest
@Transactional
class ConversationEntityTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void どちらが申し込んでもIDの小さい方がuser1になり_申し込んだ人は別に持つ() {
        User a = saveUser("a");
        User b = saveUser("b");

        Conversation fromB = persist(new Conversation(b, a));

        assertThat(fromB.getUser1().getId()).isEqualTo(a.getId());
        assertThat(fromB.getUser2().getId()).isEqualTo(b.getId());
        assertThat(fromB.getRequestedBy().getId()).isEqualTo(b.getId());
        assertThat(fromB.getStatus()).isEqualTo(ConversationStatus.REQUESTED);
        assertThat(fromB.getRequestedAt()).isNotNull();
    }

    @Test
    void 状態は定数名の文字列で保存される() {
        Conversation conversation = persist(new Conversation(saveUser("a"), saveUser("b")));
        conversation.approve();
        entityManager.flush();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM conversations WHERE id = ?", String.class, conversation.getId());
        assertThat(status).isEqualTo("ACTIVE");
        assertThat(conversation.getRespondedAt()).isNotNull();
    }

    @Test
    void 申請中の会話がある2人の間では_逆向きでも新しい申請を保存できない() {
        User a = saveUser("a");
        User b = saveUser("b");
        persist(new Conversation(a, b));

        assertThatThrownBy(() -> persist(new Conversation(b, a)))
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void 拒否や終了の会話は何件あっても_新しい申請を保存できる() {
        User a = saveUser("a");
        User b = saveUser("b");
        Conversation rejected1 = persist(new Conversation(a, b));
        rejected1.reject();
        Conversation rejected2 = persist(new Conversation(a, b));
        rejected2.reject();
        Conversation ended = persist(new Conversation(b, a));
        ended.approve();
        ended.end(b);
        entityManager.flush();

        Conversation again = persist(new Conversation(a, b));

        assertThat(again.getId()).isNotNull();
        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(ended.getEndedBy().getId()).isEqualTo(b.getId());
    }

    @Test
    void 申請中でない会話は承認できない() {
        Conversation conversation = persist(new Conversation(saveUser("a"), saveUser("b")));
        conversation.reject();

        assertThatThrownBy(conversation::approve).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 参加者でない人は終了した人として記録できない() {
        User a = saveUser("a");
        User b = saveUser("b");
        User c = saveUser("c");
        Conversation conversation = persist(new Conversation(a, b));
        conversation.approve();

        assertThatThrownBy(() -> conversation.end(c)).isInstanceOf(IllegalArgumentException.class);
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(conversation.getEndedBy()).isNull();
    }

    @Test
    void 終了した人の列は_参加者以外の値をDBに保存できない() {
        User a = saveUser("a");
        User b = saveUser("b");
        User c = saveUser("c");
        Conversation conversation = persist(new Conversation(a, b));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE conversations SET ended_by_id = ? WHERE id = ?", c.getId(), conversation.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 自分自身には申し込めない() {
        User a = saveUser("a");

        assertThatThrownBy(() -> new Conversation(a, a)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 申込時の一言を最初のメッセージとして保存し_最終メッセージ日時が進む() {
        User a = saveUser("a");
        Conversation conversation = persist(new Conversation(a, saveUser("b")));

        Message message = new Message(conversation, a, "よろしくお願いします", null);
        entityManager.persist(message);
        conversation.recordMessage(message.getSentAt());
        entityManager.flush();

        assertThat(conversation.getLastMessageAt()).isEqualTo(message.getSentAt());
        assertThat(message.getReadAt()).isNull();
        message.markAsRead();
        assertThat(message.getReadAt()).isNotNull();
    }

    @Test
    void 本文も画像もないメッセージはDBに保存できない() {
        User a = saveUser("a");
        Conversation conversation = persist(new Conversation(a, saveUser("b")));
        entityManager.persist(new Message(conversation, a, "本文", null));
        entityManager.flush();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE messages SET body = NULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User saveUser(String name) {
        return userRepository.save(new User(
                "dm-" + name + "@example.com", "0900000" + Math.abs(name.hashCode() % 10000), "hash", "dm_" + name));
    }

    /**
     * 先に未反映の変更（状態の変更など）を DB に書いてから保存する。
     * ID を DB の自動採番で決めるため、persist の時点で INSERT が先に実行され、状態の UPDATE より前になるため
     */
    private Conversation persist(Conversation conversation) {
        entityManager.flush();
        entityManager.persist(conversation);
        entityManager.flush();
        return conversation;
    }
}
