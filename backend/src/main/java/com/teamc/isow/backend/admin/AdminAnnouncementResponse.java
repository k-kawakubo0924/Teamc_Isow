package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.announcement.Announcement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理画面のお知らせ1件
 *
 * @param body 本文（文字だけ。改行は \n。画面では HTML として扱わずに表示すること）
 * @param publisherUsername 発行した管理者のユーザー名（発行した時点の値）
 */
public record AdminAnnouncementResponse(
        Long id,
        String title,
        String body,
        LocalDateTime publishedAt,
        Long publisherId,
        String publisherUsername) {

    public static AdminAnnouncementResponse from(Announcement announcement) {
        return new AdminAnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getPublishedAt(),
                announcement.getPublisherId(),
                announcement.getPublisherUsername());
    }

    /** 発行済みのお知らせの一覧（新しい順） */
    public record ListResponse(List<AdminAnnouncementResponse> items, int page, int size, boolean hasNext) {
    }
}
