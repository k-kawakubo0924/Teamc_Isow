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
 * @param endedByInactivity 30日やり取りがなかったため自動で終了したか。<b>終了の理由そのもの（ブロック・利用停止など）は返さない</b>
 *     （相手に伝わると、ブロックで隠した意味がなくなるため。ConversationEndReason）
 * @param pinned 自分がピン留めしているか（相手の設定は返さない）
 * @param muted 自分が通知をオフにしているか（相手の設定は返さない）
 */
public record ConversationDetailResponse(
        Long conversationId,
        ConversationStatus status,
        boolean requestedByMe,
        ConversationListResponse.Partner partner,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt,
        LocalDateTime endedAt,
        boolean endedByInactivity,
        boolean pinned,
        boolean muted) {
}
