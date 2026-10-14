package com.teamc.isow.backend.announcement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 利用者側のお知らせの一覧（GET /api/announcements。1ページ分）。
 *
 * <p>一覧を押したらその場で本文を開く作りのため、本文も一緒に返す（開くたびに通信しないように）。
 * 本文は最大2000文字のため、20件で約40KB。お知らせが大量に増えたら、本文を別で取る形に見直す（docs/notification.md）
 *
 * @param announcements 発行日時の新しい順
 */
public record AnnouncementListResponse(List<Item> announcements, int page, int size, boolean hasNext) {

    /**
     * お知らせの1件。管理者側の情報（発行者）は返さない
     *
     * @param body 本文（文字だけ。改行は \n。画面では HTML として扱わずに表示すること）
     * @param read 既読か（開いたもの、または登録より前に発行されたもの）
     */
    public record Item(Long id, String title, String body, LocalDateTime publishedAt, boolean read) {
    }
}
