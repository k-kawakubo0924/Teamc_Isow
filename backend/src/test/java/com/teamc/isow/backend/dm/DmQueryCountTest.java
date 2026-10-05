package com.teamc.isow.backend.dm;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DM一覧とメッセージの取得で、会話ごと・メッセージごとに SQL が発行されていないこと（N+1 問題）の確認。
 * 5件と20件で SQL の本数が同じであることを確かめる（件数に比例して増えるなら N+1 が入り込んでいる）。
 * 各会話には相手から届いた未読メッセージを付け、最新メッセージ・未読件数・相手のユーザーの読み込みも発生させる。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DmQueryCountTest {

    /** 相手のユーザーは20人まで作る。メールアドレスは名前で列挙して消す */
    private static final int MAX_PARTNERS = 20;

    @Autowired
    private ConversationListService conversationListService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private final List<User> partners = new ArrayList<>();

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(DmTestSupport.EMAILS[0], "09000000060", "hash", "dm_me"));
    }

    @AfterEach
    void tearDown() {
        DmTestSupport.cleanUp(jdbcTemplate);
        for (int i = 0; i < MAX_PARTNERS; i++) {
            jdbcTemplate.update("DELETE FROM users WHERE email = ?", partnerEmail(i));
        }
    }

    @Test
    void DM一覧とメッセージの取得は件数に関係なくSQLの本数が一定() {
        String subject = String.valueOf(me.getId());

        createConversations(5);
        long list5 = countSql(() -> conversationListService.list(subject, ConversationFilter.ALL, 0, 20),
                r -> r.conversations().size(), 5);
        Conversation first = conversationRepository.findAll().getFirst();
        addMessages(first, 4);
        // 申込時の一言 + 4通 = 5通
        long messages5 = countSql(() -> messageService.list(subject, first.getId(), null, 20),
                r -> r.messages().size(), 5);

        createConversations(15);
        long list20 = countSql(() -> conversationListService.list(subject, ConversationFilter.ALL, 0, 20),
                r -> r.conversations().size(), 20);
        addMessages(first, 15);
        long messages20 = countSql(() -> messageService.list(subject, first.getId(), null, 20),
                r -> r.messages().size(), 20);

        long sent = countSql(() -> messageService.send(subject, first.getId(), new MessageSendRequest("送信", null)),
                r -> r.body().length(), 2);

        System.out.printf("### SQL の本数: DM一覧 %d → %d、メッセージの取得 %d → %d（5件 → 20件）、送信（テキスト） %d%n",
                list5, list20, messages5, messages20, sent);
        assertThat(list20).isEqualTo(list5);
        assertThat(messages20).isEqualTo(messages5);
    }

    /** 相手から申し込まれ、承認済みの会話を作る。申込時の一言（未読）を1通付ける */
    private void createConversations(int count) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int i = 0; i < count; i++) {
                int index = partners.size();
                User partner = userRepository.save(
                        new User(partnerEmail(index), String.format("0900001%04d", index), "hash", "dm_p" + index));
                partners.add(partner);
                Conversation conversation = conversationRepository.save(new Conversation(partner, me));
                conversation.approve();
                Message message = messageRepository.save(new Message(conversation, partner, "相談" + index, null));
                conversation.recordMessage(message.getSentAt());
            }
        });
    }

    /** 会話に、相手と自分から交互にメッセージを足す */
    private void addMessages(Conversation conversation, int count) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Conversation c = conversationRepository.findById(conversation.getId()).orElseThrow();
            User partner = c.otherParticipant(me);
            for (int i = 0; i < count; i++) {
                messageRepository.save(new Message(c, i % 2 == 0 ? partner : me, "メッセージ" + i, null));
            }
        });
    }

    /** 実行した SQL の本数を返す。結果の件数も確かめる（データが取れていない状態で本数を比べないため） */
    private <T> long countSql(Supplier<T> query, java.util.function.ToIntFunction<T> size, int expectedSize) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        T result = query.get();
        long count = statistics.getPrepareStatementCount();
        assertThat(size.applyAsInt(result)).isEqualTo(expectedSize);
        return count;
    }

    private static String partnerEmail(int index) {
        return "dm-partner-" + index + "@example.com";
    }
}
