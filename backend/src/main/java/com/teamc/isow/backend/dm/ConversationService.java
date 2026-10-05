package com.teamc.isow.backend.dm;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会話の承認・拒否・終了（docs/dm.md「会話の状態」）。
 *
 * <p>状態の移り変わりは 申請中 → 進行中 → 終了、または 申請中 → 拒否 だけ。拒否・終了から先へは進めない。
 * 承認・拒否は申し込まれた側だけ、終了は当事者のどちらでもできる。
 * 確認は 当事者か（404）→ 申し込まれた側か（403）→ 状態（409）→ 上限（409、承認のみ）の順に行う。
 *
 * <p>同時に操作が届いても1つずつ処理されるよう、会話の行をロックしてから確認する。
 * 承認ではさらに、承認する人のユーザーの行をロックしてから上限を数える（別々の会話を同時に承認して上限を超えないため）。
 * ロックの順番は 会話 → ユーザー にそろえること（逆の順でロックする処理があると、互いに待ち続けることがある）。
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ConsultationService consultationService;

    public ConversationService(
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            AuthService authService,
            ConsultationService consultationService) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.consultationService = consultationService;
    }

    /** 申請を承認する（申請中 → 進行中）。申し込まれた側だけができ、自分が受けている進行中の会話が上限に達していればできない */
    @Transactional
    public ConversationResponse accept(String subject, Long conversationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        Conversation conversation = lockAsParticipant(conversationId, userId);
        requireRecipient(conversation, userId, "承認");
        requireStatus(conversation, ConversationStatus.REQUESTED, "承認");
        userRepository.findByIdForUpdate(userId);
        if (consultationService.isReceivingLimitReached(userId)) {
            throw new ConversationOperationException(ConversationOperationError.LIMIT_REACHED,
                    "相談を受けられる上限（" + consultationService.maxReceivedActiveConversations()
                            + "件）に達しているため、承認できません。進行中の相談を終了してから承認してください。");
        }
        conversation.approve();
        return ConversationResponse.from(conversation);
    }

    /** 申請を拒否する（申請中 → 拒否）。申し込まれた側だけができる。拒否した日時は24時間の再申請制限に使う */
    @Transactional
    public ConversationResponse reject(String subject, Long conversationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        Conversation conversation = lockAsParticipant(conversationId, userId);
        requireRecipient(conversation, userId, "拒否");
        requireStatus(conversation, ConversationStatus.REQUESTED, "拒否");
        conversation.reject();
        return ConversationResponse.from(conversation);
    }

    /** 会話を終了する（進行中 → 終了）。当事者のどちらでもできる。終了日時と終了した人を記録する */
    @Transactional
    public ConversationResponse end(String subject, Long conversationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        Conversation conversation = lockAsParticipant(conversationId, userId);
        requireStatus(conversation, ConversationStatus.ACTIVE, "終了");
        conversation.end(userRepository.getReferenceById(userId));
        return ConversationResponse.from(conversation);
    }

    /** 会話をロックして読み込む。存在しない場合も当事者でない場合も、同じ ConversationNotFoundException にする */
    private Conversation lockAsParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        if (!conversation.isParticipant(userId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return conversation;
    }

    private static void requireRecipient(Conversation conversation, Long userId, String operation) {
        if (conversation.getRequestedBy().getId().equals(userId)) {
            throw new ConversationOperationException(ConversationOperationError.NOT_RECIPIENT,
                    "申し込んだ本人は" + operation + "できません。");
        }
    }

    private static void requireStatus(Conversation conversation, ConversationStatus expected, String operation) {
        ConversationStatus status = conversation.getStatus();
        if (status != expected) {
            throw new ConversationOperationException(ConversationOperationError.INVALID_STATUS,
                    "この会話は" + status.getLabel() + "のため、" + operation + "できません。");
        }
    }
}
