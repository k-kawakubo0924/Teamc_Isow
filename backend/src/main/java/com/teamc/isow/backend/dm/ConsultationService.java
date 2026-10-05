package com.teamc.isow.backend.dm;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserNotFoundException;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /** まとめて確認できる人数の上限（一覧の1ページの上限と同じ） */
    public static final int MAX_STATUS_USERS = 50;

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
        return toResponse(findUnavailability(viewerId, userId, LocalDateTime.now()).orElse(null));
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

    /**
     * 複数の相手について、申し込めるかをまとめて返す（フォロー中一覧の「相談する」ボタン）。
     * 存在しないユーザーの ID は結果に含めない。人数に関係なく SQL の本数は一定
     *
     * @param userIds 相手のユーザーID（1〜MAX_STATUS_USERS 人。範囲の確認は呼び出し側で行う）
     */
    @Transactional(readOnly = true)
    public ConsultationStatusesResponse statuses(String subject, Collection<Long> userIds) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        List<Long> existing = userRepository.findAllById(new LinkedHashSet<>(userIds)).stream()
                .map(User::getId)
                .sorted()
                .toList();
        Map<Long, Unavailability> unavailable = findUnavailabilities(viewerId, existing, LocalDateTime.now());
        List<ConsultationStatusesResponse.Item> items = existing.stream()
                .map(id -> new ConsultationStatusesResponse.Item(id, toResponse(unavailable.get(id))))
                .toList();
        return new ConsultationStatusesResponse(items);
    }

    /** 申し込めない理由。申し込める場合は空。複数に当てはまる場合は ConsultationUnavailableReason の宣言順で先のもの */
    private Optional<Unavailability> findUnavailability(Long requesterId, Long recipientId, LocalDateTime now) {
        return Optional.ofNullable(findUnavailabilities(requesterId, List.of(recipientId), now).get(recipientId));
    }

    /**
     * 複数の相手について、申し込めない理由をまとめて求める（申し込める相手は結果に含めない）。
     * 1人でも複数人でも同じ判定になるよう、申込・1人分の確認・まとめての確認のすべてでこれを使う。
     * SQL は人数に関係なく4本（申請中・進行中の会話、24時間以内の拒否、受けている進行中の会話の件数 ×2）
     */
    private Map<Long, Unavailability> findUnavailabilities(
            Long requesterId, Collection<Long> recipientIds, LocalDateTime now) {
        Map<Long, Unavailability> result = new HashMap<>();
        List<Long> others = new ArrayList<>();
        for (Long recipientId : recipientIds) {
            if (requesterId.equals(recipientId)) {
                result.put(recipientId, new Unavailability(ConsultationUnavailableReason.SELF, null, null));
            } else {
                others.add(recipientId);
            }
        }
        if (others.isEmpty()) {
            return result;
        }

        for (Conversation conversation : conversationRepository.findOngoingWith(requesterId, others)) {
            ConsultationUnavailableReason reason;
            if (conversation.getStatus() == ConversationStatus.ACTIVE) {
                reason = ConsultationUnavailableReason.IN_PROGRESS;
            } else if (conversation.getRequestedBy().getId().equals(requesterId)) {
                reason = ConsultationUnavailableReason.ALREADY_REQUESTED;
            } else {
                reason = ConsultationUnavailableReason.REQUEST_RECEIVED;
            }
            result.put(partnerOf(conversation, requesterId), new Unavailability(reason, null, conversation.getId()));
        }

        // 拒否された本人だけを制限する（断った側から申し込むことは止めない）。同じ相手に複数あれば最も新しい拒否で決める
        for (Conversation rejected : conversationRepository.findRejectedSince(
                requesterId, others, ConversationStatus.REJECTED.name(), now.minus(REAPPLY_INTERVAL))) {
            Long partnerId = partnerOf(rejected, requesterId);
            LocalDateTime availableAt = rejected.getRespondedAt().plus(REAPPLY_INTERVAL);
            Unavailability current = result.get(partnerId);
            if (current == null || (current.reason() == ConsultationUnavailableReason.REJECTED_RECENTLY
                    && availableAt.isAfter(current.availableAt()))) {
                result.put(partnerId, new Unavailability(
                        ConsultationUnavailableReason.REJECTED_RECENTLY, availableAt, null));
            }
        }

        Map<Long, Long> receivedActive = new HashMap<>();
        String active = ConversationStatus.ACTIVE.name();
        for (ConversationRepository.UserCount count : conversationRepository.countReceivedAsUser1(others, active)) {
            receivedActive.merge(count.getUserId(), count.getCount(), Long::sum);
        }
        for (ConversationRepository.UserCount count : conversationRepository.countReceivedAsUser2(others, active)) {
            receivedActive.merge(count.getUserId(), count.getCount(), Long::sum);
        }
        for (Long recipientId : others) {
            if (!result.containsKey(recipientId)
                    && receivedActive.getOrDefault(recipientId, 0L) >= dmProperties.maxReceivedActiveConversations()) {
                result.put(recipientId, new Unavailability(ConsultationUnavailableReason.LIMIT_REACHED, null, null));
            }
        }
        return result;
    }

    private static Long partnerOf(Conversation conversation, Long userId) {
        Long user1Id = conversation.getUser1().getId();
        return user1Id.equals(userId) ? conversation.getUser2().getId() : user1Id;
    }

    private static ConsultationStatusResponse toResponse(Unavailability unavailability) {
        return unavailability == null
                ? ConsultationStatusResponse.ofAvailable()
                : ConsultationStatusResponse.ofUnavailable(
                        unavailability.reason(), unavailability.availableAt(), unavailability.conversationId());
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
