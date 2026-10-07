package com.teamc.isow.backend.notification;

/**
 * 通知の未読件数（GET /api/notifications/summary。ホーム画面のベルのバッジ用）。
 *
 * @param unreadCount 未読の通知の件数。メッセージの通知は含めない（DM のバッジで別に数えるため）
 */
public record NotificationSummaryResponse(long unreadCount) {
}
