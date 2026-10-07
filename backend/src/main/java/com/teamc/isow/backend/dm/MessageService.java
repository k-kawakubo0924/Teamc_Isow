package com.teamc.isow.backend.dm;

import static com.teamc.isow.backend.dm.ConversationChecks.requireParticipant;
import static com.teamc.isow.backend.dm.ConversationChecks.requireStatus;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.image.ImageUploadService;
import com.teamc.isow.backend.image.ImageUploadService.PreparedImage;
import com.teamc.isow.backend.image.InvalidImageException;
import com.teamc.isow.backend.notification.NotificationEvents;
import com.teamc.isow.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * メッセージの送信と取得（docs/dm.md「チャット画面」）。当事者でなければ、会話が存在しない場合と同じ 404 にする。
 */
@Service
public class MessageService {

    /** 1回に読むメッセージの件数の上限 */
    static final int MAX_PAGE_SIZE = 50;

    private static final String SEND = "メッセージを送信";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ImageUploadService imageUploadService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public MessageService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            AuthService authService,
            ImageUploadService imageUploadService,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.imageUploadService = imageUploadService;
        this.events = events;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    /**
     * メッセージを送信する。進行中の会話だけ送れる。送信したら会話の最終メッセージ日時を進める。
     * request はアノテーションによる入力チェック済みであること。
     *
     * <p>画像は 確認 → 画像の保存 → DB への登録 の順に行い、登録に失敗したら保存した画像を削除する（投稿の作成と同じ）。
     * 送信できるかは画像を保存する前にも確認するが、その後に会話が終了されることがあるため、登録の時点でも会話をロックして確認し直す
     */
    public MessageResponse send(String subject, Long conversationId, MessageSendRequest request) {
        Long userId = authService.requireCurrentUser(subject).getId();

        // 1. 画像を保存してから送れないことに気づくことがないよう、先にすべてを確認する
        readOnlyTransactionTemplate.executeWithoutResult(status -> requireSendable(
                requireParticipant(conversationRepository.findById(conversationId), conversationId, userId)));
        String body = request.strippedBody();
        if (body == null && !request.hasImage()) {
            throw InputValidationException.of("body", "メッセージを入力するか、画像を選択してください");
        }
        PreparedImage image = null;
        if (request.hasImage()) {
            try {
                image = imageUploadService.prepare(request.image());
            } catch (InvalidImageException e) {
                throw InputValidationException.of("image", e.getMessage());
            }
        }

        // 2. 画像を保存する
        String imageUrl = image == null ? null : imageUploadService.store(image);

        // 3. DB に登録する。失敗したら、保存した画像を削除する
        try {
            return transactionTemplate.execute(status -> save(userId, conversationId, body, imageUrl));
        } catch (RuntimeException e) {
            if (imageUrl != null) {
                imageUploadService.deleteQuietly(List.of(imageUrl));
            }
            throw e;
        }
    }

    /**
     * 会話のメッセージを古い順に返す。before を指定しなければ最新の size 件、指定すればそれより古い size 件。
     * 相手から届いた未読メッセージは、取得した範囲に関係なくすべて既読にする（チャット画面を開いたら読んだものとする）。
     * 当事者なら、終了・拒否した会話も読める
     */
    @Transactional
    public MessageListResponse list(String subject, Long conversationId, Long before, int size) {
        Long userId = openAsParticipant(subject, conversationId);
        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        // 1件多く読み、さらに古いメッセージがあるかを判定する
        List<Message> newestFirst = messageRepository.findLatest(conversationId, before, PageRequest.of(0, limit + 1));
        boolean hasMore = newestFirst.size() > limit;
        List<Message> page = new ArrayList<>(newestFirst.subList(0, Math.min(limit, newestFirst.size())));
        Collections.reverse(page);
        return new MessageListResponse(page.stream().map(m -> MessageResponse.of(m, userId)).toList(), hasMore);
    }

    /**
     * after のメッセージより新しいものを、古い順に最大 size 件返す（チャット画面を開いている間に、新しいメッセージだけを取り直すため）。
     * hasMore はさらに新しいメッセージがあるか（ある場合は、返した最後の ID を after にして続けて読む）。
     * 既読にする範囲は list と同じ
     */
    @Transactional
    public MessageListResponse listAfter(String subject, Long conversationId, Long after, int size) {
        Long userId = openAsParticipant(subject, conversationId);
        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<Message> oldestFirst = messageRepository.findAfter(conversationId, after, PageRequest.of(0, limit + 1));
        boolean hasMore = oldestFirst.size() > limit;
        List<Message> page = oldestFirst.subList(0, Math.min(limit, oldestFirst.size()));
        return new MessageListResponse(page.stream().map(m -> MessageResponse.of(m, userId)).toList(), hasMore);
    }

    /**
     * 当事者であることを確かめ、相手から届いた未読メッセージをすべて既読にする（チャット画面を開いたら読んだものとする）。
     * 先に既読にしてから読むことで、返す readAt も既読にした後の値になる。ログイン中のユーザーの ID を返す
     */
    private Long openAsParticipant(String subject, Long conversationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        requireParticipant(conversationRepository.findById(conversationId), conversationId, userId);
        messageRepository.markAsRead(conversationId, userId, LocalDateTime.now());
        return userId;
    }

    /** トランザクションの中で呼ぶ。会話をロックし、終了と同時に届いても終了した会話には登録しない */
    private MessageResponse save(Long userId, Long conversationId, String body, String imageUrl) {
        Conversation conversation = requireParticipant(
                conversationRepository.findByIdForUpdate(conversationId), conversationId, userId);
        requireSendable(conversation);
        Message message = messageRepository.save(
                new Message(conversation, userRepository.getReferenceById(userId), body, imageUrl));
        conversation.recordMessage(message.getSentAt());
        // 相手への通知（会話ごとに1件にまとめる）は、コミット後に NotificationService が作る
        events.publishEvent(new NotificationEvents.MessageSent(conversationId, userId, message.getSentAt()));
        return MessageResponse.of(message, userId);
    }

    private static void requireSendable(Conversation conversation) {
        requireStatus(conversation, ConversationStatus.ACTIVE, SEND);
    }
}
