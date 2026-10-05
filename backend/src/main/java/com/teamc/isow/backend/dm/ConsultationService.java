package com.teamc.isow.backend.dm;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserNotFoundException;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 相談の申込と、申し込めるかの判定（docs/dm.md「相談の申し込み」「拒否と再申請」「相談を受けられる件数の上限」）。
 *
 * <p>申し込めない理由の判定は、申込（request）と確認（status）で同じものを使う（findUnavailability）。
 * 相手が受けている進行中の会話の上限は、申込の時点でも確認するが、申請中は数えない仕様のため、
 * 承認の時点でもう一度確認する（ConversationService.accept が isReceivingLimitReached を使う）。
 */
@Service
public class ConsultationService {

    /** 申請を拒否されてから、同じ相手に再度申し込めるようになるまでの時間 */
    public static final Duration REAPPLY_INTERVAL = Duration.ofHours(24);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final DmProperties dmProperties;
    private final TransactionTemplate transactionTemplate;

    public ConsultationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            AuthService authService,
            DmProperties dmProperties,
            PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.dmProperties = dmProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 相談を申し込む。会話を「申請中」で作り、一言メッセージがあれば最初のメッセージとして保存する。
     * 申し込めない場合は ConsultationUnavailableException、相手が存在しない場合は UserNotFoundException
     */
    public ConsultationResponse request(String subject, ConsultationRequest request) {
        Long requesterId = authService.requireCurrentUser(subject).getId();
        Long recipientId = request.recipientId();
        try {
            return transactionTemplate.execute(
                    status -> ConsultationResponse.from(create(requesterId, recipientId, request.strippedMessage())));
        } catch (DataIntegrityViolationException e) {
            // 2人がほぼ同時に申し込む（連打を含む）と、両方が「会話なし」と判断して片方が一意制約違反になる。
            // その場合は先に保存された会話があるため、理由を判定し直して返す。
            // 理由が見つからない場合（確認の直後に相手が削除されたなど）は、本当の失敗なのでそのまま投げる
            Unavailability unavailability = transactionTemplate.execute(
                    status -> findUnavailability(requesterId, recipientId, LocalDateTime.now()).orElse(null));
            if (unavailability == null) {
                throw e;
            }
            throw new ConsultationUnavailableException(unavailability.reason());
        }
    }

    /** userId の相手に相談を申し込めるか。存在しないユーザーは UserNotFoundException */
    @Transactional(readOnly = true)
    public ConsultationStatusResponse status(String subject, Long userId) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return findUnavailability(viewerId, userId, LocalDateTime.now())
                .map(u -> ConsultationStatusResponse.ofUnavailable(u.reason(), u.availableAt(), u.conversationId()))
                .orElseGet(ConsultationStatusResponse::ofAvailable);
    }

    /**
     * トランザクションの中で呼ぶ。
     *
     * <p>既存の会話は変更せず、新しい行を追加するだけにしている。拒否された会話の ongoing は拒否した時点で NULL になり、
     * コミット済みのため、再申請しても一意制約に当たらない。
     * この中で既存の会話を変更する処理（一か月連絡がない会話の自動終了など）を加える場合は、新しい会話を保存する前に
     * flush すること。ID を DB の自動採番で決めるため、保存（save）の時点で INSERT が実行され、
     * 既存の会話の UPDATE（ongoing を NULL にする）より先になり、一意制約に当たる
     */
    private Conversation create(Long requesterId, Long recipientId, String message) {
        User recipient = userRepository.findById(recipientId).orElseThrow(() -> new UserNotFoundException(recipientId));
        findUnavailability(requesterId, recipientId, LocalDateTime.now()).ifPresent(u -> {
            throw new ConsultationUnavailableException(u.reason());
        });
        Conversation conversation = conversationRepository.save(
                new Conversation(userRepository.getReferenceById(requesterId), recipient));
        if (message != null) {
            Message first = messageRepository.save(
                    new Message(conversation, conversation.getRequestedBy(), message, null));
            conversation.recordMessage(first.getSentAt());
        }
        return conversation;
    }

    /** 申し込めない理由。申し込める場合は空。複数に当てはまる場合は ConsultationUnavailableReason の宣言順で先のもの */
    private Optional<Unavailability> findUnavailability(Long requesterId, Long recipientId, LocalDateTime now) {
        if (requesterId.equals(recipientId)) {
            return Optional.of(new Unavailability(ConsultationUnavailableReason.SELF, null, null));
        }
        Long user1Id = Math.min(requesterId, recipientId);
        Long user2Id = Math.max(requesterId, recipientId);

        Optional<Conversation> ongoing = conversationRepository.findOngoing(user1Id, user2Id);
        if (ongoing.isPresent()) {
            Conversation conversation = ongoing.get();
            ConsultationUnavailableReason reason;
            if (conversation.getStatus() == ConversationStatus.ACTIVE) {
                reason = ConsultationUnavailableReason.IN_PROGRESS;
            } else if (conversation.getRequestedBy().getId().equals(requesterId)) {
                reason = ConsultationUnavailableReason.ALREADY_REQUESTED;
            } else {
                reason = ConsultationUnavailableReason.REQUEST_RECEIVED;
            }
            return Optional.of(new Unavailability(reason, null, conversation.getId()));
        }

        // 拒否された本人だけを制限する（断った側から申し込むことは止めない）
        LocalDateTime latestRejectedAt = conversationRepository.findLatestRespondedAt(
                user1Id, user2Id, requesterId, ConversationStatus.REJECTED.name(), now.minus(REAPPLY_INTERVAL));
        if (latestRejectedAt != null) {
            return Optional.of(new Unavailability(
                    ConsultationUnavailableReason.REJECTED_RECENTLY, latestRejectedAt.plus(REAPPLY_INTERVAL), null));
        }

        if (isReceivingLimitReached(recipientId)) {
            return Optional.of(new Unavailability(ConsultationUnavailableReason.LIMIT_REACHED, null, null));
        }
        return Optional.empty();
    }

    /**
     * userId が受けている（相手から申し込まれた）「進行中」の会話が上限に達しているか。申込と承認の両方で使う。
     * トランザクションの中で呼ぶこと。承認では、同時に承認して上限を超えないよう、先にユーザーの行をロックしておくこと
     */
    public boolean isReceivingLimitReached(Long userId) {
        long received = conversationRepository.countReceived(userId, ConversationStatus.ACTIVE.name());
        return received >= dmProperties.maxReceivedActiveConversations();
    }

    /** 1人が受けられる「進行中」の会話の上限 */
    public int maxReceivedActiveConversations() {
        return dmProperties.maxReceivedActiveConversations();
    }

    /**
     * @param availableAt 再度申し込めるようになる日時（REJECTED_RECENTLY のときだけ）
     * @param conversationId 申請中・進行中の会話のID（その会話があるときだけ）
     */
    private record Unavailability(ConsultationUnavailableReason reason, LocalDateTime availableAt, Long conversationId) {
    }
}
