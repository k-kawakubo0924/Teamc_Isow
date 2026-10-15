package com.teamc.isow.backend.dm;

import static com.teamc.isow.backend.dm.ConversationChecks.requireParticipant;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 相談ごとの、自分の設定（docs/dm.md「ピン留め」「通知オフ」）。
 *
 * <ul>
 *   <li>当事者でなければ、存在しない会話と同じ 404</li>
 *   <li>自分の設定だけを変える。<b>相手には通知せず、相手への応答にも含めない</b>（相手には分からない）</li>
 *   <li>同じ操作を2回しても結果は同じ（いいね・フォローと同じ）</li>
 * </ul>
 */
@Service
public class ConversationSettingService {

    /** ピン留めできる相談の数 */
    static final int MAX_PINNED = 10;

    /** ピン留めできる状態（DM一覧に出る、やり取り中の相談。終わった相談の助言を見返せるよう、終了も含める） */
    private static final Set<ConversationStatus> PINNABLE = Set.of(ConversationStatus.ACTIVE, ConversationStatus.ENDED);

    private final ConversationRepository conversationRepository;
    private final ConversationUserSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final Clock clock;

    public ConversationSettingService(ConversationRepository conversationRepository,
            ConversationUserSettingRepository settingRepository, UserRepository userRepository, AuthService authService,
            Clock clock) {
        this.conversationRepository = conversationRepository;
        this.settingRepository = settingRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.clock = clock;
    }

    /**
     * ピン留めする。申請中・拒否は 409（INVALID_STATUS）。すでに10件なら 409（PIN_LIMIT_REACHED）。
     * すでにピン留めしていれば、上限でもそのまま成功する
     */
    @Transactional
    public ConversationSettingResponse pin(String subject, Long conversationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        Conversation conversation = participantConversation(conversationId, userId);
        if (!PINNABLE.contains(conversation.getStatus())) {
            throw new ConversationOperationException(ConversationOperationError.INVALID_STATUS,
                    "この会話は" + conversation.getStatus().getLabel() + "のため、ピン留めできません。");
        }
        // 上限の確かめと追加を1人ずつ行う（同時に押しても10件を超えないように。承認の上限と同じくユーザーの行をロックする）
        User me = userRepository.findByIdForUpdate(userId).orElseThrow();
        ConversationUserSetting setting = settingRepository.findOne(userId, conversationId)
                .orElseGet(() -> new ConversationUserSetting(me, conversation));
        if (!setting.isPinned() && settingRepository.countPinned(userId) >= MAX_PINNED) {
            throw new ConversationOperationException(ConversationOperationError.PIN_LIMIT_REACHED,
                    "ピン留めできるのは" + MAX_PINNED + "件までです。");
        }
        setting.pin(LocalDateTime.now(clock));
        settingRepository.save(setting);
        return response(conversationId, setting);
    }

    /** ピン留めを外す（していなくても成功する） */
    @Transactional
    public ConversationSettingResponse unpin(String subject, Long conversationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        participantConversation(conversationId, userId);
        return settingRepository.findOne(userId, conversationId)
                .map(setting -> {
                    setting.unpin();
                    saveOrDelete(setting);
                    return response(conversationId, setting);
                })
                .orElseGet(() -> new ConversationSettingResponse(conversationId, false, false));
    }

    private Conversation participantConversation(Long conversationId, Long userId) {
        return requireParticipant(conversationRepository.findById(conversationId), conversationId, userId);
    }

    /** どちらの設定もなくなったら行を消す */
    private void saveOrDelete(ConversationUserSetting setting) {
        if (setting.isEmpty()) {
            settingRepository.delete(setting);
        } else {
            settingRepository.save(setting);
        }
    }

    private static ConversationSettingResponse response(Long conversationId, ConversationUserSetting setting) {
        return new ConversationSettingResponse(conversationId, setting.isPinned(), setting.isMuted());
    }
}
