package com.teamc.isow.backend.dm;

import java.time.LocalDateTime;

/**
 * 会話の承認・拒否・終了の結果（操作後の会話の状態）。
 *
 * @param conversationId 会話のID
 * @param status 操作後の状態
 * @param requestedAt 申し込んだ日時
 * @param respondedAt 承認または拒否した日時。申請中は null
 * @param endedAt 終了した日時。終了していなければ null
 */
public record ConversationResponse(
        Long conversationId,
        ConversationStatus status,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt,
        LocalDateTime endedAt) {

    static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getStatus(),
                conversation.getRequestedAt(),
                conversation.getRespondedAt(),
                conversation.getEndedAt());
    }
}
