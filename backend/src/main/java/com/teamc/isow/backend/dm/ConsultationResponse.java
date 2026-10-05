package com.teamc.isow.backend.dm;

import java.time.LocalDateTime;

/**
 * 相談の申込の結果（POST /api/conversations）。
 *
 * @param conversationId 作った会話のID
 * @param status 会話の状態（申込直後は常に REQUESTED）
 * @param requestedAt 申し込んだ日時
 */
public record ConsultationResponse(Long conversationId, ConversationStatus status, LocalDateTime requestedAt) {

    static ConsultationResponse from(Conversation conversation) {
        return new ConsultationResponse(conversation.getId(), conversation.getStatus(), conversation.getRequestedAt());
    }
}
