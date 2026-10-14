package com.teamc.isow.backend.announcement;

import java.time.LocalDateTime;

/**
 * 利用者側のお知らせの一覧の1行（AnnouncementRepository.findPageForUser）
 *
 * @param readAt 一覧を見ている人が開いた日時。開いていなければ null
 */
public record AnnouncementRow(Long id, String title, String body, LocalDateTime publishedAt, LocalDateTime readAt) {
}
