package com.teamc.isow.backend.dm;

import java.util.Optional;

/** 会話の操作で共通の確認（当事者か・状態が合っているか）。ConversationService と MessageService で使う */
final class ConversationChecks {

    private ConversationChecks() {
    }

    /** 当事者の会話であること。存在しない場合も当事者でない場合も、会話の有無が分からないよう同じ ConversationNotFoundException にする */
    static Conversation requireParticipant(Optional<Conversation> found, Long conversationId, Long userId) {
        return found.filter(conversation -> conversation.isParticipant(userId))
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    /**
     * 会話が expected の状態であること。違えば INVALID_STATUS
     *
     * @param operation 文言に入れる操作の名前（「承認」「メッセージを送信」など）
     */
    static void requireStatus(Conversation conversation, ConversationStatus expected, String operation) {
        ConversationStatus status = conversation.getStatus();
        if (status != expected) {
            throw new ConversationOperationException(ConversationOperationError.INVALID_STATUS,
                    "この会話は" + status.getLabel() + "のため、" + operation + "できません。");
        }
    }
}
