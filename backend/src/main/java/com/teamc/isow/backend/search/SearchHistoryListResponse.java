package com.teamc.isow.backend.search;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自分の検索履歴（GET /api/search/history）。
 *
 * @param histories 新しい順（最大 SearchHistoryService.MAX_PER_USER 件）
 */
public record SearchHistoryListResponse(List<Item> histories) {

    /**
     * @param id 削除に使う ID
     * @param keyword 検索したキーワード（検索欄に入れ直す値）
     * @param searchedAt 最後に検索した日時
     */
    public record Item(Long id, String keyword, LocalDateTime searchedAt) {

        static Item from(SearchHistory history) {
            return new Item(history.getId(), history.getKeyword(), history.getSearchedAt());
        }
    }
}
