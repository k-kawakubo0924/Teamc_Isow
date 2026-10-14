package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.announcement.Announcement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理画面のお知らせ1件
 *
 * @param body 本文（文字だけ。改行は \n。画面では HTML として扱わずに表示すること）
 * @param publisherUsername 発行した管理者のユーザー名（発行した時点の値）
 * @param withdrawnAt 取り消した日時。取り消していなければ null
 * @param withdrawnById 取り消した管理者のユーザー ID。取り消していなければ null
 * @param withdrawnByUsername 取り消した管理者のユーザー名（取り消した時点の値）。取り消していなければ null
 */
public record AdminAnnouncementResponse(
        Long id,
        String title,
        String body,
        LocalDateTime publishedAt,
        Long publisherId,
        String publisherUsername,
        LocalDateTime withdrawnAt,
        Long withdrawnById,
        String withdrawnByUsername) {

    public static AdminAnnouncementResponse from(Announcement announcement) {
        return new AdminAnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getPublishedAt(),
                announcement.getPublisherId(),
                announcement.getPublisherUsername(),
                announcement.getWithdrawnAt(),
                announcement.getWithdrawnById(),
                announcement.getWithdrawnByUsername());
    }

    /** 発行済みのお知らせの一覧（新しい順。取り消したものも含む） */
    public record ListResponse(List<AdminAnnouncementResponse> items, int page, int size, boolean hasNext) {
    }
}
