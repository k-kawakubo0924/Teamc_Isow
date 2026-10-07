package com.teamc.isow.backend.search;

import java.util.List;

/**
 * 検索の候補ワード（GET /api/search/suggestions）。
 *
 * @param words よく使われているタグ（使われた投稿数の多い順）→ 足りない分を補った公式タグ（表示順）。最大 SearchService.MAX_SUGGESTIONS 件
 */
public record SuggestionResponse(List<Item> words) {

    /**
     * @param name タグ名（検索欄に入れる値）
     * @param source どこから選んだ候補か
     */
    public record Item(String name, Source source) {
    }

    public enum Source {
        /** 投稿でよく使われているタグ */
        POPULAR,
        /** 投稿が少なく候補が足りないため、公式タグで補ったもの */
        OFFICIAL
    }
}
