package com.teamc.isow.backend.notification;

import com.teamc.isow.backend.dm.Conversation;
import com.teamc.isow.backend.dm.ConversationRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.user.UserRepository;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 通知を作る（docs/notification.md）。
 *
 * <p>いいね・フォローなどの処理が出した NotificationEvents を、その処理のコミット後に受け取り、別のトランザクションで通知を作る。
 * 通知の作成に失敗しても、本来の処理（いいね・フォローなど）は成功のままにする。エラーはログに出すだけで、呼び出し元には投げない
 * （コミット後のため、ここで投げると、成功した処理が画面にはエラーとして返ってしまう）。
 *
 * <p>自分の操作では通知を作らない。作る前に確かめ、エラーにせず作らずに済ませる。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    /** 本来の処理のトランザクションはコミット済みのため、新しいトランザクションで書き込む */
    private final TransactionTemplate newTransaction;

    public NotificationService(
            NotificationRepository notificationRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * いいねされた。投稿者本人のいいねと、同じ人・同じ投稿の通知がすでにある場合（取り消してから再度いいねした）は作らない。
     * 連打で同時に作ろうとして一意制約違反になった場合は、先に作られた通知があるため何もしない
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLiked(NotificationEvents.Liked event) {
        run(event, () -> {
            try {
                newTransaction.executeWithoutResult(status -> createLiked(event));
            } catch (DataIntegrityViolationException e) {
                if (!notificationRepository.existsLikeNotification(event.postId(), event.likerId())) {
                    throw e;
                }
            }
        });
    }

    private void createLiked(NotificationEvents.Liked event) {
        // いいねの直後に投稿が削除された場合は、通知する先がないため作らない
        Post post = postRepository.findById(event.postId()).orElse(null);
        if (post == null
                || post.getAuthor().getId().equals(event.likerId())
                || notificationRepository.existsLikeNotification(event.postId(), event.likerId())) {
            return;
        }
        notificationRepository.save(Notification.liked(post, userRepository.getReferenceById(event.likerId())));
    }

    /** フォローされた */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowed(NotificationEvents.Followed event) {
        if (event.followeeId().equals(event.followerId())) {
            return;
        }
        run(event, () -> newTransaction.executeWithoutResult(status -> notificationRepository.save(
                Notification.followed(userRepository.getReferenceById(event.followeeId()),
                        userRepository.getReferenceById(event.followerId())))));
    }

    /** 相談が届いた（申し込まれた人に届く） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConsultationRequested(NotificationEvents.ConsultationRequested event) {
        createForConversation(event, event.conversationId(), Notification::consultationRequested);
    }

    /** 相談が承認された（申し込んだ人に届く） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConsultationApproved(NotificationEvents.ConsultationApproved event) {
        createForConversation(event, event.conversationId(), Notification::consultationApproved);
    }

    /** 相談が拒否された（申し込んだ人に届く） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConsultationRejected(NotificationEvents.ConsultationRejected event) {
        createForConversation(event, event.conversationId(), Notification::consultationRejected);
    }

    /**
     * メッセージが届いた（送った人の相手に届く）。会話ごとに1件にまとめ、すでにあれば日時を更新して未読に戻す。
     * 最初のメッセージがほぼ同時に届いて一意制約違反になった場合は、先に作られた通知があるため、日時の更新としてやり直す
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(NotificationEvents.MessageSent event) {
        run(event, () -> {
            try {
                newTransaction.executeWithoutResult(status -> createOrUpdateMessage(event));
            } catch (DataIntegrityViolationException e) {
                newTransaction.executeWithoutResult(status -> createOrUpdateMessage(event));
            }
        });
    }

    private void createOrUpdateMessage(NotificationEvents.MessageSent event) {
        Conversation conversation = conversationRepository.findById(event.conversationId()).orElseThrow();
        Long recipientId = conversation.getUser1().getId().equals(event.senderId())
                ? conversation.getUser2().getId()
                : conversation.getUser1().getId();
        notificationRepository.findMessageNotification(recipientId, event.conversationId()).ifPresentOrElse(
                notification -> notification.messageArrived(event.sentAt()),
                () -> notificationRepository.save(
                        Notification.messageReceived(conversation, userRepository.getReferenceById(event.senderId()))));
    }

    private void createForConversation(Object event, Long conversationId, Function<Conversation, Notification> factory) {
        run(event, () -> newTransaction.executeWithoutResult(status -> notificationRepository.save(
                factory.apply(conversationRepository.findById(conversationId).orElseThrow()))));
    }

    /** 通知の作成に失敗しても投げない（本来の処理はコミット済みで成功している） */
    private static void run(Object event, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("通知を作れませんでした: {}", event, e);
        }
    }
}
