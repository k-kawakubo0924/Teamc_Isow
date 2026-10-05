package com.teamc.isow.backend.dm;

import java.time.LocalDateTime;

/**
 * 会話1件（GET /api/conversations/{id}）。チャット画面の上部に相手を表示し、状態で入力欄の有無などを決めるために使う。
 *
 * @param conversationId 会話のID
 * @param status 会話の状態
 * @param requestedByMe ログイン中のユーザーが申し込んだ会話か
 * @param partner 相手のユーザー
 * @param requestedAt 申し込んだ日時
 * @param respondedAt 承認または拒否した日時。申請中は null
 * @param endedAt 終了した日時。終了していなければ null
 */
public record ConversationDetailResponse(
        Long conversationId,
        ConversationStatus status,
        boolean requestedByMe,
        ConversationListResponse.Partner partner,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt,
        LocalDateTime endedAt) {
}
