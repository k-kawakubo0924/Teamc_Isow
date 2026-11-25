package com.teamc.isow.backend.dm;

/**
 * DM の件数（GET /api/conversations/summary）。下部ナビの DM のバッジと、DM一覧の上下の件数に使う。
 *
 * @param unreadMessageCount やり取り中の会話（進行中・終了）で、相手から届いた未読メッセージの合計
 * @param receivedRequestCount 受け取った申請（申請中）の件数（「メッセージリクエスト」の件数）
 * @param sentRequestCount 自分が送った申請（申請中）の件数（「送信したリクエスト」の件数）
 * @param pinnedCount 自分がピン留めしている相談の数（DM一覧の見出しのマークのバッジ。docs/dm.md「ピン留め」）
 */
public record ConversationSummaryResponse(long unreadMessageCount, long receivedRequestCount, long sentRequestCount,
        long pinnedCount) {
}
