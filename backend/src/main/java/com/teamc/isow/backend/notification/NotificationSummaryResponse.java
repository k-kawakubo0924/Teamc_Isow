package com.teamc.isow.backend.notification;

/**
 * 通知の未読件数（GET /api/notifications/summary。ホーム画面のベルと、通知一覧のタブのバッジ用）。
 * ベルには2つの合計を出し、タブにはそれぞれを出す（docs/notification.md）
 *
 * @param unreadCount 未読の通知の件数（「いいね・フォロー」タブ）。メッセージの通知は含めない（DM のバッジで別に数えるため）
 * @param unreadAnnouncementCount 未読のお知らせの件数（「お知らせ」タブ）。取り消したもの・登録より前に発行されたものは含めない
 */
public record NotificationSummaryResponse(long unreadCount, long unreadAnnouncementCount) {
}
